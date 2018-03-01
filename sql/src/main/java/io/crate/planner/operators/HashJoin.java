/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.planner.operators;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import io.crate.analyze.OrderBy;
import io.crate.analyze.relations.AnalyzedRelation;
import io.crate.data.Row;
import io.crate.execution.dsl.phases.HashJoinPhase;
import io.crate.execution.dsl.phases.MergePhase;
import io.crate.execution.dsl.projection.EvalProjection;
import io.crate.execution.dsl.projection.builder.InputColumns;
import io.crate.execution.dsl.projection.builder.ProjectionBuilder;
import io.crate.execution.engine.join.JoinOperations;
import io.crate.execution.engine.pipeline.TopN;
import io.crate.expression.symbol.InputColumn;
import io.crate.expression.symbol.SelectSymbol;
import io.crate.expression.symbol.Symbol;
import io.crate.expression.symbol.Symbols;
import io.crate.planner.ExecutionPlan;
import io.crate.planner.PlannerContext;
import io.crate.planner.ResultDescription;
import io.crate.planner.TableStats;
import io.crate.planner.distribution.DistributionInfo;
import io.crate.planner.node.dql.join.Join;
import io.crate.planner.node.dql.join.JoinType;
import org.elasticsearch.common.collect.Tuple;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.crate.planner.operators.LogicalPlanner.NO_LIMIT;

class HashJoin extends TwoInputPlan {

    private final Symbol joinCondition;
    private final TableStats tableStats;
    @VisibleForTesting
    final AnalyzedRelation concreteRelation;

    HashJoin(LogicalPlan lhs,
             LogicalPlan rhs,
             Symbol joinCondition,
             AnalyzedRelation concreteRelation,
             TableStats tableStats) {
        super(lhs, rhs, new ArrayList<>());
        this.concreteRelation = concreteRelation;
        this.joinCondition = joinCondition;
        this.outputs.addAll(lhs.outputs());
        this.outputs.addAll(rhs.outputs());
        this.tableStats = tableStats;
    }

    public JoinType joinType() {
        return JoinType.INNER;
    }

    protected Symbol joinCondition() {
        return joinCondition;
    }

    public Map<LogicalPlan, SelectSymbol> dependencies() {
        HashMap<LogicalPlan, SelectSymbol> deps = new HashMap<>(lhs.dependencies().size() + rhs.dependencies().size());
        deps.putAll(lhs.dependencies());
        deps.putAll(rhs.dependencies());
        return deps;
    }

    @Override
    public ExecutionPlan build(PlannerContext plannerContext,
                               ProjectionBuilder projectionBuilder,
                               int limit,
                               int offset,
                               @Nullable OrderBy order,
                               @Nullable Integer pageSizeHint,
                               Row params,
                               Map<SelectSymbol, Object> subQueryValues) {


        ExecutionPlan leftExecutionPlan = lhs.build(
            plannerContext, projectionBuilder, NO_LIMIT, 0, null, null, params, subQueryValues);
        ExecutionPlan rightExecutionPlan = rhs.build(
            plannerContext, projectionBuilder, NO_LIMIT, 0, null, null, params, subQueryValues);

        LogicalPlan leftLogicalPlan = lhs;
        LogicalPlan rightLogicalPlan = rhs;

        boolean tablesSwitched = false;
        // Move smaller table to the right side
        if (lhs.numExpectedRows() < rhs.numExpectedRows()) {
            tablesSwitched = true;
            leftLogicalPlan = rhs;
            rightLogicalPlan = lhs;

            ExecutionPlan tmp = leftExecutionPlan;
            leftExecutionPlan = rightExecutionPlan;
            rightExecutionPlan = tmp;
        }

        this.outputs.clear();
        this.outputs.addAll(leftLogicalPlan.outputs());
        this.outputs.addAll(rightLogicalPlan.outputs());

        ResultDescription leftResultDesc = leftExecutionPlan.resultDescription();
        ResultDescription rightResultDesc = rightExecutionPlan.resultDescription();
        Collection<String> nlExecutionNodes = ImmutableSet.of(plannerContext.handlerNode());

        MergePhase leftMerge = null;
        MergePhase rightMerge = null;
        leftExecutionPlan.setDistributionInfo(DistributionInfo.DEFAULT_BROADCAST);
        if (JoinOperations.isMergePhaseNeeded(nlExecutionNodes, leftResultDesc, false)) {
            leftMerge = JoinOperations.buildMergePhaseForJoin(plannerContext, leftResultDesc, nlExecutionNodes);
        }
        if (nlExecutionNodes.size() == 1
            && nlExecutionNodes.equals(rightResultDesc.nodeIds())
            && !rightResultDesc.hasRemainingLimitOrOffset()) {
            // if the left and the right plan are executed on the same single node the mergePhase
            // should be omitted. This is the case if the left and right table have only one shards which
            // are on the same node
            rightExecutionPlan.setDistributionInfo(DistributionInfo.DEFAULT_SAME_NODE);
        } else {
            if (JoinOperations.isMergePhaseNeeded(nlExecutionNodes, rightResultDesc, false)) {
                rightMerge = JoinOperations.buildMergePhaseForJoin(plannerContext, rightResultDesc, nlExecutionNodes);
            }
            rightExecutionPlan.setDistributionInfo(DistributionInfo.DEFAULT_BROADCAST);
        }

        Symbol joinInput = InputColumns.create(joinCondition, outputs);
        Tuple<List<Symbol>, List<Symbol>> hashInputs =
            extractHashJoinInputsFromJoinSymbolsAndSplitPerSide(tablesSwitched);

        HashJoinPhase joinPhase = new HashJoinPhase(
            plannerContext.jobId(),
            plannerContext.nextExecutionPhaseId(),
            "hash-join",
            // JoinPhase ctor wants at least one projection
            Collections.singletonList(new EvalProjection(InputColumn.fromSymbols(outputs))),
            leftMerge,
            rightMerge,
            leftLogicalPlan.outputs().size(),
            rightLogicalPlan.outputs().size(),
            nlExecutionNodes,
            joinInput,
            hashInputs.v1(),
            hashInputs.v2(),
            Symbols.typeView(leftLogicalPlan.outputs()),
            leftLogicalPlan.estimatedRowSize(),
            leftLogicalPlan.numExpectedRows());
        return new Join(
            joinPhase,
            leftExecutionPlan,
            rightExecutionPlan,
            TopN.NO_LIMIT,
            0,
            TopN.NO_LIMIT,
            outputs.size(),
            null
        );
    }

    private Tuple<List<Symbol>, List<Symbol>> extractHashJoinInputsFromJoinSymbolsAndSplitPerSide(boolean switchedTables) {
        Map<AnalyzedRelation, List<Symbol>> hashJoinSymbols = HashJoinConditionSymbolsExtractor.extract(joinCondition);

        // First extract the symbols that belong to the concrete relation
        List<Symbol> hashJoinSymbolsForConcreteRelation = hashJoinSymbols.remove(concreteRelation);
        List<Symbol> hashInputsForConcreteRelation = InputColumns.create(
            hashJoinSymbolsForConcreteRelation,
            new InputColumns.SourceSymbols(rhs.outputs()));

        // All leftover extracted symbols belong to the other relation which might be a
        // "concrete" relation too but can already be a tree of relation.
        List<Symbol> hashJoinSymbolsForJoinTree =
            hashJoinSymbols.values().stream().flatMap(List::stream).collect(Collectors.toList());
        List<Symbol> hashInputsForJoinTree = InputColumns.create(
            hashJoinSymbolsForJoinTree,
            new InputColumns.SourceSymbols(lhs.outputs()));

        if (switchedTables) {
            return new Tuple<>(hashInputsForConcreteRelation, hashInputsForJoinTree);
        }
        return new Tuple<>(hashInputsForJoinTree, hashInputsForConcreteRelation);
    }

    @Override
    protected LogicalPlan updateSources(LogicalPlan newLeftSource, LogicalPlan newRightSource) {
        return new HashJoin(newLeftSource, newRightSource, joinCondition, concreteRelation, tableStats);
    }

    @Override
    public long numExpectedRows() {
        // We don't have any cardinality estimates, so just take the bigger table
        return Math.max(lhs.numExpectedRows(), rhs.numExpectedRows());
    }

    @Override
    public long estimatedRowSize() {
        return lhs.estimatedRowSize() + rhs.estimatedRowSize();
    }

    @Override
    public <C, R> R accept(LogicalPlanVisitor<C, R> visitor, C context) {
        return visitor.visitHashJoin(this, context);
    }
}

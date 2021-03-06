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

import io.crate.analyze.OrderBy;
import io.crate.collections.Lists2;
import io.crate.data.Row;
import io.crate.execution.dsl.projection.OrderedTopNProjection;
import io.crate.execution.dsl.projection.builder.InputColumns;
import io.crate.execution.dsl.projection.builder.ProjectionBuilder;
import io.crate.expression.symbol.SelectSymbol;
import io.crate.expression.symbol.Symbol;
import io.crate.planner.ExecutionPlan;
import io.crate.planner.Merge;
import io.crate.planner.PlannerContext;
import io.crate.planner.PositionalOrderBy;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Order extends OneInputPlan {

    final OrderBy orderBy;

    static LogicalPlan.Builder create(LogicalPlan.Builder source, @Nullable OrderBy orderBy) {
        if (orderBy == null) {
            return source;
        }
        return (tableStats, usedColumns) -> {
            Set<Symbol> allUsedColumns = new LinkedHashSet<>();
            allUsedColumns.addAll(orderBy.orderBySymbols());
            allUsedColumns.addAll(usedColumns);
            return new Order(source.build(tableStats, allUsedColumns), orderBy);
        };
    }

    Order(LogicalPlan source, OrderBy orderBy) {
        super(source, Lists2.concatUnique(source.outputs(), orderBy.orderBySymbols()));
        this.orderBy = orderBy;
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
        ExecutionPlan executionPlan = source.build(
            plannerContext, projectionBuilder, limit, offset, orderBy, pageSizeHint, params, subQueryValues);
        if (executionPlan.resultDescription().orderBy() != null) {
            // Collect applied ORDER BY eagerly to produce a optimized execution plan;
            if (source instanceof Collect) {
                return executionPlan;
            }
            // merge to finalize the sorting and apply the order of *this* operator.
            // This is likely a order by on a virtual table which is sorted as well
            executionPlan = Merge.ensureOnHandler(executionPlan, plannerContext);
        }
        InputColumns.SourceSymbols ctx = new InputColumns.SourceSymbols(source.outputs());
        OrderedTopNProjection topNProjection = new OrderedTopNProjection(
            Limit.limitAndOffset(limit, offset),
            0,
            InputColumns.create(outputs, ctx),
            InputColumns.create(orderBy.orderBySymbols(), ctx),
            orderBy.reverseFlags(),
            orderBy.nullsFirst()
        );
        PositionalOrderBy positionalOrderBy =
            executionPlan.resultDescription().nodeIds().size() == 1 ? null : PositionalOrderBy.of(orderBy, outputs);
        executionPlan.addProjection(
            topNProjection,
            limit,
            offset,
            positionalOrderBy
        );
        return executionPlan;
    }

    @Override
    public LogicalPlan tryOptimize(@Nullable LogicalPlan pushDown) {
        if (pushDown instanceof Order) {
            // We can overwrite this Order with the Order being pushed down
            // because the order of the results will be changed anyway further
            // downstream.
            return source.tryOptimize(pushDown);
        }
        if (pushDown != null) {
            // already pushing down something else
            return null;
        }
        // try pushing down this Order (if possible)
        LogicalPlan optimize = source.tryOptimize(this);
        if (optimize == null) {
            return super.tryOptimize(null);
        }
        return optimize;
    }

    @Override
    protected LogicalPlan updateSource(LogicalPlan newSource) {
        // Replaces any Symbols which were part of the old orderBy
        // with the Symbols of the new orderBy at the same position.
        // This ensures that the orderBy is performed performed
        // correctly when it's sources have changed.
        // See tryOptimize of Union
        List<Symbol> oldOutputs = source.outputs();
        List<Symbol> newOutputs = newSource.outputs();
        OrderBy newOrderBy = this.orderBy.copyAndReplace(
            (symbol) -> {
                int idx = oldOutputs.indexOf(symbol);
                if (idx != -1) {
                    return newOutputs.get(idx);
                }
                throw new IllegalStateException("All symbols of this OrderBy should be replaceable " +
                                                "with Symbols of the new source.");
            }
        );
        return new Order(newSource, newOrderBy);
    }

    @Override
    public String toString() {
        return "Order{" +
               "src=" + source +
               ", " + orderBy +
               '}';
    }

    @Override
    public <C, R> R accept(LogicalPlanVisitor<C, R> visitor, C context) {
        return visitor.visitOrder(this, context);
    }
}

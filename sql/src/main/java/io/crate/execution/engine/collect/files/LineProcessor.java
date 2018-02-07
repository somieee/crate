/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.execution.engine.collect.files;

import io.crate.execution.dsl.phases.FileUriCollectPhase.InputFormat;
import io.crate.expression.reference.file.LineContext;
import io.crate.operation.collect.files.CSVLineParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class LineProcessor {

    private LineContext lineContext;
    private static String header;

    public void startCollect(Iterable<LineCollectorExpression<?>> collectorExpressions) {
        lineContext = new LineContext();
        for (LineCollectorExpression<?> collectorExpression : collectorExpressions) {
            collectorExpression.startCollect(lineContext);
        }
    }

    public void readFirstLine(URI currentUri, InputFormat inputFormat, BufferedReader currentReader) throws IOException {
        if (isInputCsv(inputFormat, currentUri)) {
            header = currentReader.readLine();
        }
    }

    public void process(String line, InputFormat inputFormat, URI currentUri) throws IOException {
        byte[] lineAsByteArray = line.getBytes(StandardCharsets.UTF_8);

        if (isInputCsv(inputFormat, currentUri)) {
            byte[] convertedCsvToJson = convertCsvToJson(header, lineAsByteArray);
            lineContext.rawSource(convertedCsvToJson);
        } else {
            lineContext.rawSource(lineAsByteArray);
        }
    }

    private boolean isInputCsv(InputFormat inputFormat, URI currentUri) {
        return (inputFormat == InputFormat.CSV) || currentUri.toString().endsWith(".csv");
    }

    private byte[] convertCsvToJson(String header, byte[] lineAsByteArray) throws IOException {
        CSVLineParser csvParser = new CSVLineParser();
        return csvParser.parse(header.getBytes(StandardCharsets.UTF_8), lineAsByteArray);
    }
}

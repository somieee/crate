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

package io.crate.operation.collect.files;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CSVLineParser {

    public static byte[] parse(byte[] header, byte[] row) throws IOException {

        ByteArrayInputStream headerInputStream = new ByteArrayInputStream(header);
        ByteArrayInputStream rowInputStream = new ByteArrayInputStream(row);

        Reader headerReader = new InputStreamReader(headerInputStream, StandardCharsets.UTF_8);
        Reader rowReader = new InputStreamReader(rowInputStream, StandardCharsets.UTF_8);
        CSVParser headerParser = CSVFormat
            .DEFAULT
            .withFirstRecordAsHeader()
            .withTrim()
            .parse(headerReader);

        CSVParser rowParser = CSVFormat
            .DEFAULT
            .withTrim()
            .parse(rowReader);

        try {
            final Set<String> keys = headerParser.getHeaderMap().keySet();
            return convertCSVToJsonString(keys, rowParser);
        } finally {
            headerInputStream.close();
            rowInputStream.close();
            headerReader.close();
            rowReader.close();
            headerParser.close();
            rowParser.close();
        }
    }

    private static byte[] convertCSVToJsonString(Set<String> keys, CSVParser rowParser) throws JsonProcessingException {
        Map<String,String> mapForSingleRow = Collections.emptyMap();

        List<String> keyList = getListOfKeys(keys);

        for (CSVRecord rowEntries : rowParser) {
            if (rowEntries.size() != keyList.size()) {
                throw new IllegalArgumentException("Number of row entries is not equal to the number of columns");
            }

            mapForSingleRow = getMapOfKeysAndRowEntries(keyList, rowEntries);
        }

        return new ObjectMapper().writeValueAsBytes(mapForSingleRow);
    }

    private static List<String> getListOfKeys(Set<String> keys) {
        keys.removeIf(item -> item == null || "".equals(item));
        return new ArrayList<>(keys);
    }

    private static Map<String, String> getMapOfKeysAndRowEntries(List<String> keys, CSVRecord rowEntries) {
        return IntStream.range(0, keys.size())
            .boxed()
            .collect(Collectors.toMap(keys::get, rowEntries::get));
    }
}

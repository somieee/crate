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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CSVLineParser {

    public static final String ILLEGAL_ARGUMENT_EX_STRING = "Number of row entries is not equal to the number of columns";

    public String parse(byte[] header, byte[] row) throws IOException {

        CSVParser headerParser = CSVFormat
            .DEFAULT
            .withFirstRecordAsHeader()
            .withTrim()
            .parse(new InputStreamReader(new ByteArrayInputStream(header), StandardCharsets.UTF_8));

        CSVParser rowParser = CSVFormat
            .DEFAULT
            .withTrim()
            .parse(new InputStreamReader(new ByteArrayInputStream(row), StandardCharsets.UTF_8));

        final Set<String> keys = headerParser.getHeaderMap().keySet();

        String parsedCsv = convertCSVToJsonString(keys, rowParser);

        headerParser.close();
        rowParser.close();
        return parsedCsv;
    }

    private String convertCSVToJsonString(Set<String> keys, CSVParser rowParser) throws JsonProcessingException {
        Map<String,String> mapForSingleRow = Collections.emptyMap();

        for (CSVRecord rowEntries : rowParser) {
            List<String> keyList = getListOfKeys(keys);

            if (rowEntries.size() != keyList.size()) throw new IllegalArgumentException(ILLEGAL_ARGUMENT_EX_STRING);
            mapForSingleRow = getMapOfKeysAndRowEntries(keyList, rowEntries);
        }

        return new ObjectMapper().writeValueAsString(mapForSingleRow);
    }

    private List<String> getListOfKeys(Set<String> keys) {
        keys.removeIf(item -> item == null || "".equals(item));
        return new ArrayList<>(keys);
    }

    private Map<String, String> getMapOfKeysAndRowEntries(List<String> keys, CSVRecord rowEntries) {
        return IntStream.range(0, keys.size())
            .boxed()
            .collect(Collectors.toMap(
                keys::get,
                rowEntries::get,
                (key, value) -> {
                    throw new IllegalArgumentException(ILLEGAL_ARGUMENT_EX_STRING); },
                LinkedHashMap::new));
    }
}

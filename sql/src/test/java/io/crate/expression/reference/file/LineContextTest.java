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

package io.crate.expression.reference.file;

import io.crate.metadata.ColumnIdent;
import io.crate.test.integration.CrateUnitTest;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;

public class LineContextTest extends CrateUnitTest {

    LineContext subjectUnderTest;

    @Before
    public void setup(){
        subjectUnderTest = new LineContext();
    }

    @Test
    public void testGet() {
        String source = "{\"name\": \"foo\", \"details\": {\"age\": 43}}";
        subjectUnderTest.rawSource(source.getBytes(StandardCharsets.UTF_8));

        assertNull(subjectUnderTest.get(new ColumnIdent("invalid", "column")));
        assertNull(subjectUnderTest.get(new ColumnIdent("details", "invalid")));
        assertEquals(43, subjectUnderTest.get(new ColumnIdent("details", "age")));
    }

    @Test
    public void rawSourceCSV_givenByteInputForHeaderAndRow_thenAssignsParsedJsonToRawSource() throws IOException {
        String header = "name,id\n";
        String line = "Arthur,4\n";

        subjectUnderTest.rawSourceFromCSV(header.getBytes(StandardCharsets.UTF_8),line.getBytes(StandardCharsets.UTF_8));

        thenRawSourceIsAssignedAs("{\"name\":\"Arthur\",\"id\":\"4\"}".getBytes(StandardCharsets.UTF_8));

    }

    private void thenRawSourceIsAssignedAs(byte[] expected) {
        assertThat(subjectUnderTest.rawSource, is(expected));
    }
}

package io.crate.operation.collect.files;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CSVLineParserTest {

    CSVLineParser subjectUnderTest;

    private static byte[] headerByteArray;
    private static byte[] rowByteArray;
    private String result;

    @Before
    public void setup(){
        subjectUnderTest = new CSVLineParser();
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_givenEmptyHeader_thenThrowsException() throws IOException {
        givenHeader("\n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_givenDuplicateKey_thenThrowsException() throws IOException {
        givenHeader("Code,Country,Country\n");
        givenRow("GER,Germany,Another\n");

        whenParseIsCalled();
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_givenMissingKey_thenThrowsException() throws IOException {
        givenHeader("Code,\n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_givenExtraKey_thenThrowsException() throws IOException {
        givenHeader("Code,Country,Another\n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();
    }

    @Test
    public void parse_givenCSVInput_thenParsesToJson() throws IOException {
        givenHeader("Code,Country\n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();

        thenResultIs("{\"Code\":\"GER\",\"Country\":\"Germany\"}");
    }

    @Test
    public void parse_givenEmptyRow_thenParsesToEmptyJson() throws IOException {
        givenHeader("Code,Country\n");
        givenRow("\n");

        whenParseIsCalled();

        thenResultIs("{}");
    }

    @Test
    public void parse_givenEscapedComma_thenParsesLineCorrectly() throws IOException {
        givenHeader("Code,\"Coun, try\"\n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();

        thenResultIs("{\"Code\":\"GER\",\"Coun, try\":\"Germany\"}");
    }


    @Test
    public void parse_givenRowWithMissingValue_thenTheValueIsAssignedToKeyAsAnEmptyString() throws IOException {
        givenHeader("Code,Country,City\n");
        givenRow("GER,,Berlin\n");

        whenParseIsCalled();

        thenResultIs("{\"Code\":\"GER\",\"Country\":\"\",\"City\":\"Berlin\"}");
    }


    @Test
    public void parse_givenTrailingWhiteSpaceInHeader_thenParsesToJsonWithoutWhitespace() throws IOException {
        givenHeader("Code ,Country  \n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();

        thenResultIs("{\"Code\":\"GER\",\"Country\":\"Germany\"}");
    }

    @Test
    public void parse_givenTrailingWhiteSpaceInRow_thenParsesToJsonWithoutWhitespace() throws IOException {
        givenHeader("Code,Country\n");
        givenRow("GER        ,Germany\n");

        whenParseIsCalled();

        thenResultIs("{\"Code\":\"GER\",\"Country\":\"Germany\"}");
    }

    @Test
    public void parse_givenPrecedingWhiteSpaceInHeader_thenParsesToJsonWithoutWhitespace() throws IOException {
        givenHeader("         Code,         Country\n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();

        thenResultIs("{\"Code\":\"GER\",\"Country\":\"Germany\"}");
    }

    @Test
    public void parse_givenPrecedingWhiteSpaceInRow_thenParsesToJsonWithoutWhitespace() throws IOException {
        givenHeader("Code,Country\n");
        givenRow("GER,               Germany\n");

        whenParseIsCalled();

        thenResultIs("{\"Code\":\"GER\",\"Country\":\"Germany\"}");
    }

    private void givenHeader(String header) {
        headerByteArray = header.getBytes(StandardCharsets.UTF_8);
    }

    private void givenRow(String row) {
        rowByteArray = row.getBytes(StandardCharsets.UTF_8);
    }

    private void whenParseIsCalled() throws IOException {
        result = subjectUnderTest.parse(headerByteArray, rowByteArray);
    }

    private void thenResultIs(String expected) throws IOException {
        assertThat(result, is(expected));
    }

}

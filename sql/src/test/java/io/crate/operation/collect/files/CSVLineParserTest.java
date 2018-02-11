package io.crate.operation.collect.files;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CSVLineParserTest {

    private static byte[] headerByteArray;
    private static byte[] rowByteArray;
    private byte[] result;

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

        assertThat(result, is("{\"Country\":\"Germany\",\"Code\":\"GER\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parse_givenEmptyRow_thenParsesToEmptyJson() throws IOException {
        givenHeader("Code,Country\n");
        givenRow("\n");

        whenParseIsCalled();

        assertThat(result, is("{}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parse_givenEscapedComma_thenParsesLineCorrectly() throws IOException {
        givenHeader("Code,\"Coun, try\"\n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();

        assertThat(result, is("{\"Coun, try\":\"Germany\",\"Code\":\"GER\"}".getBytes(StandardCharsets.UTF_8)));
    }


    @Test
    public void parse_givenRowWithMissingValue_thenTheValueIsAssignedToKeyAsAnEmptyString() throws IOException {
        givenHeader("Code,Country,City\n");
        givenRow("GER,,Berlin\n");

        whenParseIsCalled();

        assertThat(result, is("{\"Country\":\"\",\"City\":\"Berlin\",\"Code\":\"GER\"}".getBytes(StandardCharsets.UTF_8)));
    }


    @Test
    public void parse_givenTrailingWhiteSpaceInHeader_thenParsesToJsonWithoutWhitespace() throws IOException {
        givenHeader("Code ,Country  \n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();

        assertThat(result, is("{\"Country\":\"Germany\",\"Code\":\"GER\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parse_givenTrailingWhiteSpaceInRow_thenParsesToJsonWithoutWhitespace() throws IOException {
        givenHeader("Code,Country\n");
        givenRow("GER        ,Germany\n");

        whenParseIsCalled();

        assertThat(result, is("{\"Country\":\"Germany\",\"Code\":\"GER\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parse_givenPrecedingWhiteSpaceInHeader_thenParsesToJsonWithoutWhitespace() throws IOException {
        givenHeader("         Code,         Country\n");
        givenRow("GER,Germany\n");

        whenParseIsCalled();

        assertThat(result, is("{\"Country\":\"Germany\",\"Code\":\"GER\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parse_givenPrecedingWhiteSpaceInRow_thenParsesToJsonWithoutWhitespace() throws IOException {
        givenHeader("Code,Country\n");
        givenRow("GER,               Germany\n");

        whenParseIsCalled();

        assertThat(result, is("{\"Country\":\"Germany\",\"Code\":\"GER\"}".getBytes(StandardCharsets.UTF_8)));
    }

    private void givenHeader(String header) {
        headerByteArray = header.getBytes(StandardCharsets.UTF_8);
    }

    private void givenRow(String row) {
        rowByteArray = row.getBytes(StandardCharsets.UTF_8);
    }

    private void whenParseIsCalled() throws IOException {
        result = CSVLineParser.parse(headerByteArray, rowByteArray);
    }
}

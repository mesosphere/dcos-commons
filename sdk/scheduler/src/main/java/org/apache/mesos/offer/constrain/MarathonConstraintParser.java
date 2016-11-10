package org.apache.mesos.offer.constrain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MarathonConstraintParser {

    private static final TypeReference<List<String>> LIST_TYPE_REFERENCE =
            new TypeReference<List<String>>(){};
    private static final TypeReference<List<List<String>>> LIST_LIST_TYPE_REFERENCE =
            new TypeReference<List<List<String>>>(){};

    /**
     * Creates a new constraint parser against the provided string.
     *
     * @param constraint the json-formatted marathon-style constraint string
     * @see https://mesosphere.github.io/marathon/docs/constraints.html
     */
    public static PlacementRule parse(String constraint) throws IOException {
        List<List<String>> rows;
        ObjectMapper mapper = new ObjectMapper();
        try {
            // First try list of strings (not technically legal but lets be lenient here)
            rows = Arrays.asList(mapper.readValue(constraint, LIST_TYPE_REFERENCE));
        } catch (IOException e) {
            // Then try list of lists of strings (throw if this also fails)
            rows = mapper.readValue(constraint, LIST_LIST_TYPE_REFERENCE);
        }
        if (rows.size() == 1) {
            return parseRow(rows.get(0));
        } else {
            List<PlacementRule> rowRules = new ArrayList<>();
            for (List<String> row : rows) {
                rowRules.add(parseRow(row));
            }
            return new AndRule(rowRules);
        }
    }

    /**
     * Converts the provided marathon constraint entry to a PlacementRule
     */
    private static PlacementRule parseRow(List<String> row) throws IOException {
        if (row.size() < 2 || row.size() > 3) {
            throw new IOException(String.format(
                    "Invalid number of entries in rule. Expected 2-3, got %s: %s",
                    row.size(), row));
        }
    }
}

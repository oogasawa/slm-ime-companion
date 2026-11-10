package com.github.oogasawa.slmimecompanion.historyreview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A choice of the user history as the server notes it in {@code choices.jsonl}: the reading of a bunsetsu, the text
 * chosen for it, and the whole text it was chosen in (its reading {@code input} and the text as committed
 * {@code output}, empty when not known).
 */
record Choice(String reading, String chosen, String input, String output) {

    /** The choice of a line of {@code choices.jsonl}, or {@code null} for a line that is not one. */
    static Choice parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            String reading = text(o, "reading");
            String chosen = text(o, "chosen");
            if (reading.isEmpty() || chosen.isEmpty()) {
                return null;
            }
            return new Choice(reading, chosen, text(o, "input"), text(o, "output"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The key a choice is judged by: the same reading and text are judged once, whatever text they came in. */
    String key() {
        return reading + "\t" + chosen;
    }

    /** The line of {@code suspects/*.jsonl} for this choice. */
    String suspectLine() {
        JsonObject o = new JsonObject();
        o.addProperty("reading", reading);
        o.addProperty("chosen", chosen);
        return o.toString();
    }

    /** The line of {@code choices.jsonl} for this choice, for one that is to be asked again. */
    String choiceLine() {
        JsonObject o = new JsonObject();
        o.addProperty("reading", reading);
        o.addProperty("chosen", chosen);
        o.addProperty("input", input);
        o.addProperty("output", output);
        return o.toString();
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }
}

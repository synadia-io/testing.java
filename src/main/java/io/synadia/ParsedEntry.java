package io.synadia;

import io.nats.client.api.KeyValueEntry;
import io.nats.client.support.JsonParseException;
import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.support.Constants.FINAL;

public class ParsedEntry {
    public final KeyValueEntry kve;
    public final JsonValue jv;
    public final boolean fin;
    public final String key;
    public final String statType;
    public final String contextId;
    public final String statId;
    public String label;
    public Object target;
    public boolean reported;

    public ParsedEntry(KeyValueEntry kve) throws JsonParseException {
        this.kve = kve;
        jv = JsonParser.parse(kve.getValue());

        JsonValue jvFinal = jv.map.get(FINAL);
        if (jvFinal == null) {
            fin = false;
        }
        else {
            fin = jvFinal.bool != null && jvFinal.bool;
        }

        key = kve.getKey();
        String[] parts = key.split("\\.");
        statType = parts[0];
        contextId = parts[1];
        statId = parts.length == 3 ? parts[2] : "";
    }

    static final AtomicInteger CONTEXT_ID = new AtomicInteger(0);
    static final Map<String, Integer> idByContext = new HashMap<>();
    static final Map<Integer, Map<String, Integer>> statIdsForContext = new HashMap<>();
    static final Map<Integer, AtomicInteger> statsIdMakerForContext = new HashMap<>();

    public void targetAndLabel(Object target, boolean contextOnly) {
        this.target = target;
        label = contextOnly
            ? String.format("%s-%03d", statType, getContextCode(this))
            : String.format("%s-%03d-%03d", statType, getContextCode(this), getStatIdCode(this));
    }

    static int getContextCode(ParsedEntry p) {
        Integer cid = idByContext.get(p.contextId);
        if (cid == null) {
            cid = CONTEXT_ID.incrementAndGet();
            idByContext.put(p.contextId, cid);
        }
        return cid;
    }

    static int getStatIdCode(ParsedEntry p) {
        Integer cid = getContextCode(p);
        Map<String, Integer> map = statIdsForContext.computeIfAbsent(cid, k -> new HashMap<>());
        Integer sid = map.get(p.statId);
        if (sid == null) {
            AtomicInteger maker = statsIdMakerForContext.computeIfAbsent(cid, k -> new AtomicInteger());
            sid = maker.incrementAndGet();
            map.put(p.statId, sid);
        }
        return sid;
    }

    public static void sort(List<ParsedEntry> list) {
        list.sort(Comparator.comparing(p -> p.label));
    }
}

package io.synadia.workloads.support;

import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonSerializable;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.synadia.workloads.AbstractCustomWorkload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.nats.jsmulti.shared.Stats.humanTime;
import static io.synadia.utils.Commons.*;

public class Event implements JsonSerializable {

    private final AbstractCustomWorkload abstractCustomWorkload;
    public final String job;
    public final String workId;
    public final int tix;
    public final String qualifier;
    public final boolean defaultQualifier;
    public final long count;
    public final long elapsed;
    public final String exceptionClass;
    public final String exceptionMessage;

    public Event(AbstractCustomWorkload abstractCustomWorkload, byte[] jsonBytes) {
        this.abstractCustomWorkload = abstractCustomWorkload;
        JsonValue jv = JsonParser.parseUnchecked(jsonBytes);
        this.job = JsonValueUtils.readString(jv, "job");
        this.workId = JsonValueUtils.readString(jv, "work_id");
        this.tix = JsonValueUtils.readInteger(jv, "tix", NO_TIX);
        this.qualifier = JsonValueUtils.readString(jv, "qualifier");
        this.defaultQualifier = qualifier == null || qualifier.trim().isEmpty();
        this.count = JsonValueUtils.readLong(jv, "count", 0);
        this.elapsed = JsonValueUtils.readLong(jv, "elapsed", 0);

        this.exceptionClass = JsonValueUtils.readString(jv, "exception_class");
        this.exceptionMessage = JsonValueUtils.readString(jv, "exception_message");
    }

    public Event(AbstractCustomWorkload abstractCustomWorkload, String job, String workId, int tix, String qualifier, long count, long elapsed, Exception exception) {
        this.abstractCustomWorkload = abstractCustomWorkload;
        this.job = job;
        this.workId = workId;
        this.tix = tix;
        this.defaultQualifier = qualifier == null || qualifier.trim().isEmpty();
        if (defaultQualifier) {
            this.qualifier = null;
        }
        else {
            this.qualifier = qualifier
                .replace(" ", "")
                .replace("*", "")
                .replace(">", "")
                .replace(".", "").trim();
        }
        this.count = count;
        this.elapsed = elapsed;
        if (exception == null) {
            exceptionClass = null;
            exceptionMessage = null;
        }
        else {
            exceptionClass = exception.getClass().getSimpleName();
            exceptionMessage = exception.getMessage();
        }
    }

    @Override
    public String toJson() {
        JsonValueUtils.MapBuilder mb = JsonValueUtils.mapBuilder();
        mb.put("job", job);
        if (tix != NO_TIX) {
            mb.put("tix", tix);
        }
        mb.put("work_id", workId);
        mb.put("qualifier", qualifier);
        mb.put("count", count);
        mb.put("elapsed", elapsed);
        mb.put("exception_class", exceptionClass);
        mb.put("exception_message", exceptionMessage);
        return mb.jv.toJson();
    }

    @Override
    public String toString() {
        return toJson();
    }

    public Object[] extras(String... messages) {
        List<Object> list = new ArrayList<>();
        if (count > 0) {
            list.add("Count: " + count);
        }
        if (exceptionClass != null) {
            list.add(exceptionClass + ": " + exceptionMessage);
        }
        list.add(humanTime(elapsed));
        if (messages != null) {
            for (String m : messages) {
                if (m != null) {
                    list.add(m);
                }
            }
        }
        return list.toArray();
    }

    public String subject() {
        return (exceptionClass == null ? abstractCustomWorkload.logSubjectPrefix : abstractCustomWorkload.exSubjectPrefix)
            + segments(DEFAULT_SEGMENT);
    }

    public String ident() {
        return segments("");
    }

    private String segments(String missing) {
        return job
            // + (workId == null    ? missing : DOT + workId)
            + (defaultQualifier ? missing : DOT + qualifier)
            + (tix == NO_TIX ? missing : DOT + tix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Event event = (Event) o;
        return tix == event.tix
            && defaultQualifier == event.defaultQualifier
            && count == event.count
            && elapsed == event.elapsed
            && Objects.equals(job, event.job)
            && Objects.equals(workId, event.workId)
            && Objects.equals(qualifier, event.qualifier)
            && Objects.equals(exceptionClass, event.exceptionClass)
            && Objects.equals(exceptionMessage, event.exceptionMessage);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(job);
        result = 31 * result + Objects.hashCode(workId);
        result = 31 * result + tix;
        result = 31 * result + Objects.hashCode(qualifier);
        result = 31 * result + Boolean.hashCode(defaultQualifier);
        result = 31 * result + Long.hashCode(count);
        result = 31 * result + Long.hashCode(elapsed);
        result = 31 * result + Objects.hashCode(exceptionClass);
        result = 31 * result + Objects.hashCode(exceptionMessage);
        return result;
    }
}

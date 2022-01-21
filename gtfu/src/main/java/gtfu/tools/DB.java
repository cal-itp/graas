package gtfu.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.datastore.*;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StructuredQuery;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;

import gtfu.Debug;
import gtfu.Util;

public class DB {

    // ### remove github secret REPORT_FIRESTORE_ACCESS, replace with 4 individual sensitive fields from docker-auth.json
    public List<String> fetch(long minTimestamp, long maxTimestamp, String kind, String[] propertyNames) throws Exception {
        StringBuffer sb = new StringBuffer();

        Debug.log("DB.fetch()");
        Debug.log("- minTimestamp: " + minTimestamp);
        Debug.log("- maxTimestamp: " + maxTimestamp);
        Debug.log("- propertyNames: " + Util.arrayToString(propertyNames));

        List<String> list = new ArrayList<String>();

        for (int i=0; i<propertyNames.length; i++) {
            if (i > 0) {
                sb.append(",");
            }

            sb.append(propertyNames[i]);
        }

        list.add(sb.toString());

        Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
        // Note that at least one dependency (DayLogslicer.java) requires that this query be ordered by timestamp
        Query<Entity> query = Query.newEntityQueryBuilder()
                                    .setKind(kind)
                                    .setFilter(
                                        CompositeFilter.and(PropertyFilter.ge("timestamp", minTimestamp),
                                                            PropertyFilter.lt("timestamp", maxTimestamp)))
                                    .setOrderBy(OrderBy.asc("timestamp"))
                                    .build();
        QueryResults<Entity> results = datastore.run(query);

        while (results.hasNext()) {
            Entity e = results.next();
            boolean missingField = false;

            sb.setLength(0);

            for (String key : propertyNames) {
                if (!e.contains(key)) {
                    missingField = true;
                    continue;
                }

                if (sb.length() > 0) {
                    sb.append(",");
                }

                sb.append(e.getValue(key).get());
            }

            if (missingField) continue;

            String line = sb.toString();
            list.add(line);
        }

        return list;
    }
}
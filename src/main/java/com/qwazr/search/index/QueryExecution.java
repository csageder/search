/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.index;

import com.qwazr.search.collector.ConcurrentCollector;
import com.qwazr.search.field.SortUtils;
import com.qwazr.search.query.DrillDownQuery;
import com.qwazr.utils.ClassLoaderUtils;
import com.qwazr.utils.TimeTracker;
import com.qwazr.utils.concurrent.ConcurrentUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class QueryExecution<T extends ResultDocumentAbstract> {

    final QueryContextImpl queryContext;
    final QueryDefinition queryDef;
    final TimeTracker timeTracker;
    final Map<String, String> dimensions;
    final FacetsConfig facetsConfig;
    final int start;
    final int rows;
    final int end;
    final Sort sort;
    final boolean bNeedScore;
    final boolean useDrillSideways;
    final Query query;
    final List<Pair<Constructor, Object[]>> collectorConstructors;

    private final boolean isConcurrent;

    QueryExecution(final QueryContextImpl queryContext, final QueryDefinition queryDefinition)
            throws QueryNodeException, ReflectiveOperationException, ParseException, IOException {

        this.timeTracker = new TimeTracker();

        this.queryContext = queryContext;
        this.queryDef = queryDefinition;

        this.query = queryDef.luceneQuery != null ?
                queryDef.luceneQuery :
                queryDef.query == null ? new MatchAllDocsQuery() : queryDef.query.getQuery(queryContext);

        this.sort = queryDef.sorts == null ? null : SortUtils.buildSort(queryContext.fieldMap, queryDef.sorts);

        this.dimensions = queryDef.facets == null ? null : FacetsBuilder.getFields(queryDef.facets);
        this.facetsConfig = dimensions == null ? null : queryContext.fieldMap.getFacetsConfig(dimensions);

        this.start = queryDef.getStartValue();
        this.rows = queryDef.getRowsValue();
        this.end = Math.min(start + rows, queryContext.indexReader.numDocs());

        this.bNeedScore = sort == null || sort.needsScores();
        this.useDrillSideways =
                queryDef.query instanceof DrillDownQuery && ((DrillDownQuery) queryDef.query).useDrillSideways &&
                        queryDef.facets != null;
        if (queryDef.collectors != null && !queryDef.collectors.isEmpty()) {
            collectorConstructors = new ArrayList<>();
            isConcurrent = buildExternalCollectors(queryDef.collectors, collectorConstructors);
        } else {
            collectorConstructors = null;
            isConcurrent = true;
        }
    }

    private static boolean buildExternalCollectors(final Map<String, QueryDefinition.CollectorDefinition> collectors,
                                                   final List<Pair<Constructor, Object[]>> collectorConstructors) throws ReflectiveOperationException {
        if (collectors == null || collectors.isEmpty())
            return true; // By default we use concurrent
        final AtomicInteger concurrentCollectors = new AtomicInteger(0);
        final AtomicInteger classicCollectors = new AtomicInteger(0);
        ConcurrentUtils.forEachEx(collectors, (name, collector) -> {
            final Class<? extends Collector> collectorClass = ClassLoaderUtils.findClass(collector.classname);
            Constructor<?>[] constructors = collectorClass.getConstructors();
            if (constructors.length == 0)
                throw new ReflectiveOperationException("No constructor for class: " + collectorClass);
            final Constructor<?> constructor;
            final Object[] arguments;
            if (collector.arguments == null || collector.arguments.length == 0) {
                constructor = collectorClass.getConstructor(String.class);
                arguments = new Object[]{name};
            } else {
                arguments = new Object[collector.arguments.length + 1];
                arguments[0] = name;
                System.arraycopy(collector.arguments, 0, arguments, 1, collector.arguments.length);
                Class[] classes = new Class[arguments.length];
                int i = 0;
                for (Object arg : arguments)
                    classes[i++] = arg.getClass();
                constructor = collectorClass.getConstructor(classes);
            }
            collectorConstructors.add(Pair.of(constructor, arguments));
            if (ConcurrentCollector.class.isAssignableFrom(collectorClass))
                concurrentCollectors.incrementAndGet();
            else
                classicCollectors.incrementAndGet();
        });
        if (concurrentCollectors.get() > 0 && classicCollectors.get() > 0)
            throw new IllegalArgumentException("Cannot mix concurrent collectors and classic collectors");
        return concurrentCollectors.get() > 0 || classicCollectors.get() == 0;
    }

    final ResultDefinition<T> execute(final ResultDocuments<T> resultDocuments) throws Exception {

        final ResultDocumentsInterface resultDocumentsInterface = resultDocuments.getResultDocuments();

        final QueryCollectors queryCollectors =
                isConcurrent ? new QueryCollectorManager(this) : new QueryCollectorsClassic(this);

        final FacetsBuilder facetsBuilder = queryCollectors.execute();

        final TopDocs topDocs = queryCollectors.getTopDocs();
        final Integer totalHits = queryCollectors.getTotalHits();

        final Map<String, HighlighterImpl> highlighters;
        if (queryDef.highlighters != null && topDocs != null) {
            highlighters = new LinkedHashMap<>();
            queryDef.highlighters.forEach((name, highlighterDefinition) -> highlighters.put(name,
                    new HighlighterImpl(name, highlighterDefinition, queryContext)));
        } else
            highlighters = null;

        timeTracker.next("search_query");

        final ResultDocumentsBuilder resultBuilder =
                new ResultDocumentsBuilder(queryDef, topDocs, queryContext.indexSearcher, query, highlighters,
                        queryCollectors.getExternalResults(), timeTracker, facetsBuilder,
                        totalHits == null ? 0 : totalHits, resultDocumentsInterface);

        return resultDocuments.apply(resultBuilder);
    }

    final Explanation explain(final int docId) throws IOException {
        return queryContext.indexSearcher.explain(query, docId);
    }
}

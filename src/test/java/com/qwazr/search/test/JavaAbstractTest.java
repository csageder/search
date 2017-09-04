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
package com.qwazr.search.test;

import com.google.common.io.Files;
import com.qwazr.search.SearchServer;
import com.qwazr.search.analysis.AnalyzerDefinition;
import com.qwazr.search.annotations.AnnotatedIndexService;
import com.qwazr.search.collector.MaxNumericCollector;
import com.qwazr.search.collector.MinNumericCollector;
import com.qwazr.search.field.CustomFieldDefinition;
import com.qwazr.search.field.FieldDefinition;
import com.qwazr.search.index.BackupStatus;
import com.qwazr.search.index.FacetDefinition;
import com.qwazr.search.index.FieldStats;
import com.qwazr.search.index.IndexCheckStatus;
import com.qwazr.search.index.IndexServiceInterface;
import com.qwazr.search.index.IndexSettingsDefinition;
import com.qwazr.search.index.IndexStatus;
import com.qwazr.search.index.QueryBuilder;
import com.qwazr.search.index.QueryDefinition;
import com.qwazr.search.index.RemoteIndex;
import com.qwazr.search.index.ResultDefinition;
import com.qwazr.search.index.ResultDocumentObject;
import com.qwazr.search.index.SchemaSettingsDefinition;
import com.qwazr.search.index.TermDefinition;
import com.qwazr.search.index.TermEnumDefinition;
import com.qwazr.search.query.DrillDownQuery;
import com.qwazr.search.query.JoinQuery;
import com.qwazr.search.query.LongExactQuery;
import com.qwazr.search.query.LongMultiRangeQuery;
import com.qwazr.search.query.LongRangeQuery;
import com.qwazr.search.query.LongSetQuery;
import com.qwazr.search.query.MatchAllDocsQuery;
import com.qwazr.search.query.MultiFieldQuery;
import com.qwazr.search.query.QueryParserOperator;
import com.qwazr.search.query.TermQuery;
import com.qwazr.search.query.TermsQuery;
import com.qwazr.search.query.WildcardQuery;
import com.qwazr.server.RemoteService;
import com.qwazr.utils.http.HttpResponseEntityException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.search.join.ScoreMode;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.ws.rs.WebApplicationException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.BiFunction;

import static com.qwazr.search.test.JsonAbstractTest.checkErrorStatusCode;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class JavaAbstractTest {

	public static final String[] RETURNED_FIELDS =
			{ FieldDefinition.ID_FIELD, "title", "content", "price", "storedCategory", "serialValue", "externalValue" };

	protected abstract IndexServiceInterface getIndexService() throws URISyntaxException, IOException;

	static final File backupDir = Files.createTempDir();

	private final IndexSettingsDefinition indexSlaveDefinition;

	public static synchronized <T> AnnotatedIndexService<T> getService(final IndexServiceInterface indexService,
			final Class<T> indexClass, final String indexName, final IndexSettingsDefinition settings)
			throws URISyntaxException {
		return new AnnotatedIndexService<>(indexService, indexClass, null, indexName, settings);
	}

	public static synchronized <T> AnnotatedIndexService<T> getService(final IndexServiceInterface indexService,
			final Class<T> indexClass) throws URISyntaxException {
		return getService(indexService, indexClass, null, null);
	}

	protected JavaAbstractTest(final IndexSettingsDefinition indexSlaveDefinition) {
		this.indexSlaveDefinition = indexSlaveDefinition;
	}

	private AnnotatedIndexService<AnnotatedIndex> getMaster() throws URISyntaxException, IOException {
		return getService(getIndexService(), AnnotatedIndex.class);
	}

	private AnnotatedIndexService<AnnotatedIndex> getSlave() throws URISyntaxException, IOException {
		return getService(getIndexService(), AnnotatedIndex.class, AnnotatedIndex.INDEX_NAME_SLAVE,
				indexSlaveDefinition);
	}

	@Test
	public void test000startServer() throws Exception {
		TestServer.startServer();
		Assert.assertNotNull(TestServer.service);
		Assert.assertNotNull(SearchServer.getInstance().getClusterManager());
		Assert.assertNotNull(SearchServer.getInstance().getIndexManager());
	}

	@Test
	public void test010CheckMasterClient() throws URISyntaxException, IOException {
		final AnnotatedIndexService master = getMaster();
		Assert.assertEquals(AnnotatedIndex.SCHEMA_NAME, master.getSchemaName());
		Assert.assertEquals(AnnotatedIndex.INDEX_NAME_MASTER, master.getIndexName());
	}

	@Test
	public void test050CreateSchema() throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		final SchemaSettingsDefinition settings1 = service.createUpdateSchema();
		Assert.assertNotNull(settings1);
		final SchemaSettingsDefinition settings2 = service.createUpdateSchema(settings1);
		Assert.assertNotNull(settings2);
		Assert.assertEquals(settings1, settings2);
		final SchemaSettingsDefinition settings =
				SchemaSettingsDefinition.of().backupDirectoryPath(backupDir.getAbsolutePath()).build();
		final SchemaSettingsDefinition settings3 = service.createUpdateSchema(settings);
		Assert.assertEquals(settings, settings3);
	}

	@Test
	public void test060CreateMasterIndex() throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		final IndexStatus indexStatus1 = service.createUpdateIndex();
		Assert.assertNotNull(indexStatus1);
		Assert.assertNotNull(indexStatus1.index_uuid);
		final IndexStatus indexStatus2 = service.getIndexStatus();
		Assert.assertEquals(indexStatus1, indexStatus2);
	}

	@Test
	public void test070FieldChangesNoFields() throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		final Map<String, AnnotatedIndexService.FieldStatus> fieldChanges = service.getFieldChanges();
		Assert.assertNotNull(fieldChanges);
		Assert.assertEquals(17, fieldChanges.size());
		fieldChanges.forEach(
				(s, fieldStatus) -> Assert.assertEquals(AnnotatedIndexService.FieldStatus.EXISTS_ONLY_IN_ANNOTATION,
						fieldStatus));
	}

	@Test
	public void test072CreateUpdateFields() throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		LinkedHashMap<String, FieldDefinition> fields = service.createUpdateFields();
		Assert.assertNotNull(fields);
		CustomFieldDefinition field;
		Assert.assertNotNull("The Title field is not present", field = (CustomFieldDefinition) fields.get("title"));
		Assert.assertEquals("en.EnglishAnalyzer", field.analyzer);
		Assert.assertNotNull("The Content field is not present", field = (CustomFieldDefinition) fields.get("content"));
		Assert.assertEquals(EnglishAnalyzer.class.getName(), field.analyzer);
		Assert.assertNotNull("The Category field is not present",
				field = (CustomFieldDefinition) fields.get("category"));
		Assert.assertEquals(FieldDefinition.Template.SortedSetDocValuesFacetField, field.template);
		Assert.assertNotNull("The Price field is not present", field = (CustomFieldDefinition) fields.get("price"));
		Assert.assertEquals(FieldDefinition.Template.DoubleDocValuesField, field.template);
	}

	@Test
	public void test074GetFields() throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		LinkedHashMap<String, FieldDefinition> fields = service.getFields();
		Assert.assertNotNull(fields);
		Assert.assertFalse(fields.isEmpty());
		fields.forEach((fieldName, fieldDefinition) -> {
			CustomFieldDefinition fieldDef = (CustomFieldDefinition) service.getField(fieldName);
			Assert.assertNotNull(fieldDef);
			Assert.assertEquals(fieldDef.template, ((CustomFieldDefinition) fieldDefinition).template);
			Assert.assertEquals(fieldDef.analyzer, ((CustomFieldDefinition) fieldDefinition).analyzer);
		});
	}

	@Test
	public void test076FieldNoChanges() throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		final Map<String, AnnotatedIndexService.FieldStatus> fieldChanges = service.getFieldChanges();
		Assert.assertNotNull(fieldChanges);
		Assert.assertEquals(0, fieldChanges.size());
	}

	private void checkTermDef(List<TermDefinition> terms, int expectedSize) {
		Assert.assertNotNull(terms);
		Assert.assertEquals(expectedSize, terms.size());
	}

	@Test
	public void test080GetAnalyzers() throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		LinkedHashMap<String, AnalyzerDefinition> analyzers = service.getAnalyzers();
		Assert.assertNotNull(analyzers);
		Assert.assertTrue(analyzers.isEmpty());
		analyzers.forEach((analyzerName, analyzerDefinition) -> {
			AnalyzerDefinition anaDef = service.getAnalyzer(analyzerName);
			Assert.assertNotNull(anaDef);
			checkTermDef(service.testAnalyzer(analyzerName, "Please analyzer this text"), 3);
		});
	}

	@Test
	public void test082doAnalyzer() throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		checkTermDef(service.doAnalyzeIndex("content", "Please analyzer this text"), 3);
		checkTermDef(service.doAnalyzeQuery("content", "Please analyzer this text"), 3);
	}

	private final static AnnotatedIndex record1 =
			new AnnotatedIndex(1, "First article title", "Content of the first article", 0d, 10L, true, false, "news",
					"economy").multiFacet("cat", "news", "economy");

	private final static AnnotatedIndex record2 =
			new AnnotatedIndex(2, "Second article title", "Content of the second article", 0d, 20L, true, false, "news",
					"science").multiFacet("cat", "news", "science");

	private AnnotatedIndex checkRecord(AnnotatedIndex refRecord)
			throws URISyntaxException, ReflectiveOperationException, IOException {
		final AnnotatedIndexService<AnnotatedIndex> service = getMaster();
		AnnotatedIndex record = service.getDocument(refRecord.id);
		Assert.assertNotNull(record);
		return record;
	}

	@Test
	public void test100PostDocument()
			throws URISyntaxException, IOException, InterruptedException, ReflectiveOperationException {
		final AnnotatedIndexService<AnnotatedIndex> service = getMaster();
		service.postDocument(record1);
		AnnotatedIndex newRecord1 = checkRecord(record1);
		Assert.assertEquals(record1, newRecord1);
	}

	@Test
	public void test110PostDocuments()
			throws URISyntaxException, IOException, InterruptedException, ReflectiveOperationException {
		final AnnotatedIndexService<AnnotatedIndex> service = getMaster();
		service.postDocuments(Arrays.asList(record1, record2));
		AnnotatedIndex newRecord1 = checkRecord(record1);
		Assert.assertEquals(record1, newRecord1);
		AnnotatedIndex newRecord2 = checkRecord(record2);
		Assert.assertEquals(record2, newRecord2);
		Assert.assertEquals(new Long(2), service.getIndexStatus().num_docs);
	}

	private final static AnnotatedIndex docValue1 = new AnnotatedIndex(1, null, null, 1.11d, null, false, true);
	private final static AnnotatedIndex docValue2 = new AnnotatedIndex(2, null, null, 2.22d, null, false, true);

	@Test
	public void test200UpdateDocValues() throws URISyntaxException, IOException, InterruptedException {
		final AnnotatedIndexService service = getMaster();
		service.updateDocumentValues(docValue1);
	}

	@Test
	public void test210UpdateDocsValues()
			throws URISyntaxException, IOException, InterruptedException, ReflectiveOperationException {
		final AnnotatedIndexService service = getMaster();
		service.updateDocumentsValues(Arrays.asList(docValue1, docValue2));
		checkRecord(record1);
		checkRecord(record2);
	}

	private ResultDefinition.WithObject<AnnotatedIndex> checkQueryResult(QueryBuilder builder, Long expectedHits)
			throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		builder.returnedFields(RETURNED_FIELDS);
		ResultDefinition.WithObject<AnnotatedIndex> result = service.searchQuery(builder.build());
		Assert.assertNotNull(result);
		if (expectedHits != null)
			Assert.assertEquals(expectedHits, result.total_hits);
		return result;
	}

	@Test
	public void test300SimpleTermQuery() throws URISyntaxException, IOException {
		QueryBuilder builder = QueryDefinition.of(new TermQuery(FieldDefinition.ID_FIELD, "1"));
		checkQueryResult(builder, 1L);
	}

	@Test
	public void test301MultiTermQuery() throws URISyntaxException, IOException {
		QueryBuilder builder = QueryDefinition.of(new TermsQuery(FieldDefinition.ID_FIELD, "1", "2"));
		checkQueryResult(builder, 2L);
	}

	@Test
	public void test302WildcardQuery() throws URISyntaxException, IOException {
		QueryBuilder builder = QueryDefinition.of(new WildcardQuery("title", "art*"));
		checkQueryResult(builder, 2L);
		builder = QueryDefinition.of(new WildcardQuery("title", "*econ?"));
		checkQueryResult(builder, 1L);
	}

	@Test
	public void test320PointExactQuery() throws URISyntaxException, IOException {
		QueryBuilder builder = QueryDefinition.of(new LongExactQuery(AnnotatedIndex.QUANTITY_FIELD, 10));
		ResultDefinition.WithObject<AnnotatedIndex> result = checkQueryResult(builder, 1L);
		Assert.assertEquals("1", result.documents.get(0).record.id);
	}

	@Test
	public void test320PointSetQuery() throws URISyntaxException, IOException {
		QueryBuilder builder = QueryDefinition.of(new LongSetQuery(AnnotatedIndex.QUANTITY_FIELD, 20, 25));
		ResultDefinition.WithObject<AnnotatedIndex> result = checkQueryResult(builder, 1L);
		Assert.assertEquals("2", result.documents.get(0).record.id);
	}

	@Test
	public void test320PointRangeQuery() throws URISyntaxException, IOException {
		QueryBuilder builder = QueryDefinition.of(new LongRangeQuery(AnnotatedIndex.QUANTITY_FIELD, 15L, 25L));
		ResultDefinition.WithObject<AnnotatedIndex> result = checkQueryResult(builder, 1L);
		Assert.assertEquals("2", result.documents.get(0).record.id);
	}

	@Test
	public void test320PointMultiRangeQuery() throws URISyntaxException, IOException {
		LongMultiRangeQuery.Builder qBuilder = new LongMultiRangeQuery.Builder(AnnotatedIndex.QUANTITY_FIELD);
		qBuilder.addRange(15L, 25L);
		QueryBuilder builder = QueryDefinition.of(qBuilder.build());
		checkQueryResult(builder, 1L);
	}

	private ResultDocumentObject<AnnotatedIndex> checkResultDocument(
			ResultDefinition<ResultDocumentObject<AnnotatedIndex>> result, int pos) {
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.documents);
		Assert.assertTrue(result.documents.size() > pos);
		ResultDocumentObject<AnnotatedIndex> resultDocument = result.documents.get(pos);
		Assert.assertNotNull(resultDocument);
		return resultDocument;
	}

	private void checkEqualsReturnedFields(AnnotatedIndex record, AnnotatedIndex recordRef,
			AnnotatedIndex docValueRef) {
		Assert.assertEquals(recordRef.title, record.title);
		Assert.assertEquals(recordRef.content, record.content);
		Assert.assertEquals(docValueRef.price, record.price);
		Assert.assertArrayEquals(recordRef.storedCategory.toArray(), record.storedCategory.toArray());
		Assert.assertEquals(recordRef.externalValue, record.externalValue);
		Assert.assertEquals(recordRef.serialValue, record.serialValue);
	}

	private final void testReturnedFieldQuery(String... returnedFields) throws URISyntaxException, IOException {
		final AnnotatedIndexService service = getMaster();
		QueryBuilder builder = QueryDefinition.of(new TermQuery(FieldDefinition.ID_FIELD, record2.id.toString()));
		builder.returnedField(returnedFields);
		ResultDefinition.WithObject<AnnotatedIndex> result = service.searchQuery(builder.build());
		Assert.assertNotNull(result);
		Assert.assertEquals(new Long(1), result.total_hits);
		AnnotatedIndex returnedRecord = checkResultDocument(result, 0).record;
		checkEqualsReturnedFields(returnedRecord, record2, docValue2);
	}

	@Test
	public void test350ReturnedFieldQuery() throws URISyntaxException, IOException {
		testReturnedFieldQuery(RETURNED_FIELDS);
	}

	@Test
	public void test360ReturnedFieldQueryAll() throws URISyntaxException, IOException {
		testReturnedFieldQuery("*");
	}

	@Test
	public void test400getDocumentById() throws ReflectiveOperationException, URISyntaxException, IOException {
		final AnnotatedIndexService<AnnotatedIndex> master = getMaster();
		AnnotatedIndex record = master.getDocument(record1.id);
		checkEqualsReturnedFields(record, record1, docValue1);
	}

	@Test
	public void test420getDocuments() throws ReflectiveOperationException, URISyntaxException, IOException {
		final AnnotatedIndexService<AnnotatedIndex> master = getMaster();
		List<AnnotatedIndex> records = master.getDocuments(0, 2);
		Assert.assertNotNull(records);
		Assert.assertEquals(2L, records.size());
		AnnotatedIndex doc1 = records.get(0);
		AnnotatedIndex doc2 = records.get(1);
		if (record1.id.equals(doc1.id))
			checkEqualsReturnedFields(doc1, record1, docValue1);
		else
			checkEqualsReturnedFields(doc1, record2, docValue2);
		if (record2.id.equals(doc2.id))
			checkEqualsReturnedFields(doc2, record2, docValue2);
		else
			checkEqualsReturnedFields(doc2, record1, docValue1);
	}

	private void testSort(QueryBuilder queryBuilder, int resultCount,
			BiFunction<AnnotatedIndex, AnnotatedIndex, Boolean> checker) throws URISyntaxException, IOException {
		final AnnotatedIndexService<AnnotatedIndex> master = getMaster();
		ResultDefinition.WithObject<AnnotatedIndex> result = master.searchQuery(queryBuilder.build());
		Assert.assertNotNull(result);
		Assert.assertEquals(Long.valueOf(resultCount), result.total_hits);
		Assert.assertNotNull(result.documents);
		Assert.assertEquals(resultCount, result.documents.size());
		AnnotatedIndex current = null;
		for (ResultDocumentObject<AnnotatedIndex> resultDoc : result.documents) {
			final AnnotatedIndex next = resultDoc.getRecord();
			if (current != null)
				Assert.assertTrue(checker.apply(current, next));
			current = next;
		}
	}

	@Test
	public void test500sortByTitleDescAndScore() throws URISyntaxException, IOException {
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery());
		builder.sort("titleSort", QueryDefinition.SortEnum.descending_missing_first)
				.sort(FieldDefinition.SCORE_FIELD, QueryDefinition.SortEnum.descending)
				.returnedField("title")
				.start(0)
				.rows(100);
		testSort(builder, 2, (doc1, doc2) -> doc1.title.compareTo(doc2.title) > 0);
	}

	@Test
	public void test500sortByTitleAscAndScore() throws URISyntaxException, IOException {
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery())
				.sort("titleSort", QueryDefinition.SortEnum.ascending_missing_last)
				.sort(FieldDefinition.SCORE_FIELD, QueryDefinition.SortEnum.descending)
				.returnedField("title")
				.start(0)
				.rows(100);
		testSort(builder, 2, (doc1, doc2) -> doc1.title.compareTo(doc2.title) < 0);
	}

	@Test
	public void test500sortByLongAsc() throws URISyntaxException, IOException {
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery())
				.sort("dvQty", QueryDefinition.SortEnum.ascending_missing_last)
				.sort(FieldDefinition.SCORE_FIELD, QueryDefinition.SortEnum.ascending)
				.returnedField("dvQty")
				.start(0)
				.rows(100);
		testSort(builder, 2, (doc1, doc2) -> doc1.dvQty.compareTo(doc2.dvQty) < 0);
	}

	@Test
	public void test500sortByLongDesc() throws URISyntaxException, IOException {
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery())
				.sort("dvQty", QueryDefinition.SortEnum.descending_missing_last)
				.sort(FieldDefinition.SCORE_FIELD, QueryDefinition.SortEnum.descending)
				.returnedField("dvQty")
				.start(0)
				.rows(100);
		testSort(builder, 2, (doc1, doc2) -> doc1.dvQty.compareTo(doc2.dvQty) > 0);
	}

	@Test
	public void test500sortByDoubleAsc() throws URISyntaxException, IOException {
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery())
				.sort("price", QueryDefinition.SortEnum.ascending_missing_last)
				.sort(FieldDefinition.SCORE_FIELD, QueryDefinition.SortEnum.ascending)
				.returnedField("price")
				.start(0)
				.rows(100);
		testSort(builder, 2, (doc1, doc2) -> doc1.price.compareTo(doc2.price) < 0);
	}

	@Test
	public void test500sortByDoubleDesc() throws URISyntaxException, IOException {
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery())
				.sort("price", QueryDefinition.SortEnum.descending_missing_last)
				.sort(FieldDefinition.SCORE_FIELD, QueryDefinition.SortEnum.descending)
				.returnedField("price")
				.start(0)
				.rows(100);
		testSort(builder, 2, (doc1, doc2) -> doc1.price.compareTo(doc2.price) > 0);
	}

	public static List<TermEnumDefinition> checkTermList(List<TermEnumDefinition> terms) {
		Assert.assertNotNull(terms);
		Assert.assertFalse(terms.isEmpty());
		return terms;
	}

	static void checkFieldStats(String fieldName, FieldStats fieldStats) {
		Assert.assertNotNull(fieldStats);
		if (fieldStats.numberOfTerms == null)
			return;
		Assert.assertNotNull(fieldName, fieldStats.docCount);
		Assert.assertNotNull(fieldName, fieldStats.sumDocFreq);
		Assert.assertNotNull(fieldName, fieldStats.sumTotalTermFreq);
	}

	@Test
	public void test600getFieldStats() throws IOException, URISyntaxException {
		final AnnotatedIndexService<AnnotatedIndex> master = getMaster();
		master.getFields().keySet().forEach(fieldName -> checkFieldStats(fieldName, master.getFieldStats(fieldName)));
	}

	@Test
	public void test610TermsEnum() throws URISyntaxException, IOException {
		final AnnotatedIndexService<AnnotatedIndex> master = getMaster();
		int firstSize = checkTermList(master.doExtractTerms("content", null, null, 10000)).size();
		int secondSize = checkTermList(master.doExtractTerms("content", null, 2, 10000)).size();
		Assert.assertEquals(firstSize, secondSize + 2);
		checkTermList(master.doExtractTerms("content", "a", null, null));
	}

	@Test
	public void test612IndexStatus() throws URISyntaxException, IOException {
		final AnnotatedIndexService<AnnotatedIndex> master = getMaster();
		final IndexStatus indexStatus = master.getIndexStatus();
		Assert.assertNotNull(indexStatus.segment_infos);
		Assert.assertFalse(indexStatus.segment_infos.isEmpty());
		Assert.assertNotNull(indexStatus.segments_bytes_size);
		Assert.assertNotNull(indexStatus.segments_size);
		Assert.assertNotNull(indexStatus.query_cache);
		Assert.assertNotNull(indexStatus.directory_cached_ram_used);
	}

	@Test
	public void test650checkIndex() throws IOException, URISyntaxException {
		final AnnotatedIndexService<AnnotatedIndex> master = getMaster();
		IndexCheckStatus status = master.checkIndex();
		Assert.assertNotNull(status);
	}

	private void checkMultiField(final MultiFieldQuery query, final String check, final int size)
			throws URISyntaxException, IOException {
		final AnnotatedIndexService<AnnotatedIndex> master = getMaster();
		final QueryBuilder builder = QueryDefinition.of(query).queryDebug(true);
		final ResultDefinition.WithObject<AnnotatedIndex> result = master.searchQuery(builder.build());
		Assert.assertEquals(check, result.getQuery());
		Assert.assertEquals(size, result.documents.size());
	}

	@Test
	public void test700MultiFieldWithFuzzy() throws IOException, ReflectiveOperationException, URISyntaxException {
		Map<String, Float> fields = new LinkedHashMap<>();
		fields.put("title", 10.0F);
		fields.put("titleStd", 5.0F);
		fields.put("content", 1.0F);
		MultiFieldQuery query = new MultiFieldQuery(fields, QueryParserOperator.AND, "title sekond", null);
		checkMultiField(query,
				"+((title:titl)^10.0 (titleStd:title)^5.0 content:titl) +((title:sekond~2)^10.0 (titleStd:sekond~2)^5.0 content:sekond~2)",
				1);
		query = new MultiFieldQuery(fields, QueryParserOperator.OR, "title sekond", 2);
		checkMultiField(query,
				"(((title:titl)^10.0 (titleStd:title)^5.0 content:titl) ((title:sekond~2)^10.0 (titleStd:sekond~2)^5.0 content:sekond~2))~2",
				1);
		query = new MultiFieldQuery(QueryParserOperator.OR, "title sekond", 1).boost("title", 10.0F)
				.boost("titleStd", 5.0F)
				.boost("content", 1.0F);
		checkMultiField(query,
				"(((title:titl)^10.0 (titleStd:title)^5.0 content:titl) ((title:sekond~2)^10.0 (titleStd:sekond~2)^5.0 content:sekond~2))~1",
				2);
	}

	@Test
	public void test710MultiFieldWithDisjunction()
			throws IOException, ReflectiveOperationException, URISyntaxException {
		MultiFieldQuery query =
				new MultiFieldQuery(QueryParserOperator.AND, "title second", null, 0.1F).boost("title", 10.0F)
						.boost("titleStd", 5.0F)
						.boost("content", 1.0F);
		checkMultiField(query,
				"+((title:titl)^10.0 | (titleStd:title)^5.0 | content:titl)~0.1 +((title:second)^10.0 | (titleStd:second)^5.0 | content:second)~0.1",
				1);
	}

	@Test
	public void test800replicationCheck() throws URISyntaxException, IOException {
		final AnnotatedIndexService master = getMaster();
		final IndexStatus masterStatus = master.getIndexStatus();
		Assert.assertNotNull(masterStatus);
		Assert.assertNotNull(masterStatus.index_uuid);
		Assert.assertNotNull(masterStatus.version);

		final LinkedHashMap<String, FieldDefinition> masterFields = master.getFields();
		final LinkedHashMap<String, AnalyzerDefinition> masterAnalyzers = master.getAnalyzers();

		final AnnotatedIndexService slave = getSlave();

		// Let's first create an empty index (without master)
		final IndexStatus nonSlaveStatus =
				getIndexService().createUpdateIndex(slave.getSchemaName(), slave.getIndexName(),
						IndexSettingsDefinition.of().enableTaxonomyIndex(false).build());
		Assert.assertNotNull(nonSlaveStatus);
		Assert.assertNull(nonSlaveStatus.master_uuid);
		Assert.assertNotNull(nonSlaveStatus.settings);
		Assert.assertNull(nonSlaveStatus.settings.master);

		// Then we set a wrong master node
		try {
			final IndexStatus wrongMasterStatus =
					getIndexService().createUpdateIndex(slave.getSchemaName(), slave.getIndexName(),
							IndexSettingsDefinition.of()
									.master(new RemoteIndex(RemoteService.of("http://localhost:9092"), "dummy",
											"dummy"))
									.build());
			Assert.fail("The exception has not been thrown");
		} catch (WebApplicationException e) {
			Assert.assertEquals(500, e.getResponse().getStatus());
			String message = e.getMessage();
			if (!e.getMessage().contains("dummy"))
				message = e.getResponse().readEntity(String.class);
			Assert.assertTrue(message.contains("dummy"));
		} catch (HttpHostConnectException e) {
			//OK
		} catch (HttpResponseEntityException e) {
			Assert.assertEquals(500, e.getStatusCode());
		}

		// Now we set the real slave index
		final IndexStatus indexStatus = slave.createUpdateIndex();
		Assert.assertNotNull(indexStatus);
		Assert.assertNotNull(indexStatus.index_uuid);
		Assert.assertNotNull(indexStatus.settings);
		Assert.assertNotNull(indexStatus.settings.master);
		Assert.assertNotNull(indexStatus.settings.master.schema);
		Assert.assertNotNull(indexStatus.settings.master.index);
		Assert.assertNotNull(indexStatus.master_uuid);

		// Second call to check setting comparison
		Assert.assertNotNull(slave.createUpdateIndex());

		// First replication call
		slave.replicationCheck();

		// Second replication call (nothing to do)
		slave.replicationCheck();

		final IndexStatus slaveStatus = slave.getIndexStatus();
		Assert.assertNotNull(slaveStatus);
		Assert.assertNotNull(slaveStatus.version);
		Assert.assertEquals(slaveStatus.master_uuid, masterStatus.index_uuid);

		Assert.assertEquals(masterStatus.version, slaveStatus.version);
		Assert.assertEquals(masterStatus.num_docs, slaveStatus.num_docs);

		final LinkedHashMap<String, FieldDefinition> slaveFields = slave.getFields();
		final LinkedHashMap<String, AnalyzerDefinition> slaveAnalyzers = slave.getAnalyzers();
		Assert.assertNotNull(slaveFields);
		Assert.assertNotNull(slaveAnalyzers);

		Assert.assertArrayEquals(slaveFields.keySet().toArray(), masterFields.keySet().toArray());
		Assert.assertArrayEquals(slaveAnalyzers.keySet().toArray(), masterAnalyzers.keySet().toArray());
	}

	private final static String BACKUP_NAME = "myBackup";

	@Test
	public void test850backup() throws IOException, URISyntaxException {
		final AnnotatedIndexService master = getMaster();
		SortedMap<String, SortedMap<String, BackupStatus>> globalStatus = master.getBackups("*", true);
		Assert.assertNotNull(globalStatus);
		globalStatus = master.doBackup(BACKUP_NAME);
		Assert.assertNotNull(globalStatus);
		final SortedMap<String, BackupStatus> schemaStatus = globalStatus.get(master.getSchemaName());
		Assert.assertNotNull(schemaStatus);
		final BackupStatus backupStatus = schemaStatus.get(master.getIndexName());
		Assert.assertNotNull(backupStatus);
		Assert.assertNotNull(backupStatus.date);
		Assert.assertNotNull(backupStatus.files_count);
	}

	@Test
	public void test900join() throws URISyntaxException, IOException {
		final AnnotatedIndexService master = getMaster();
		final QueryBuilder builder = QueryDefinition.of(
				new JoinQuery(AnnotatedIndex.INDEX_NAME_SLAVE, "docValuesCategory", "storedCategory", true,
						ScoreMode.Max, new MatchAllDocsQuery()));
		final ResultDefinition.WithObject<AnnotatedIndex> result = master.searchQuery(builder.build());
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.total_hits);
		Assert.assertEquals(new Long(2), result.total_hits);
	}

	static void checkCollector(ResultDefinition result, String name, Object... possibleValues) {
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.collectors);
		Object collectorResult = result.getCollector(name);
		if (possibleValues == null || possibleValues.length == 0) {
			Assert.assertNull(collectorResult);
		} else {
			Assert.assertNotNull(collectorResult);
			for (Object value : possibleValues) {
				if (value.equals(collectorResult)) {
					Assert.assertEquals(value, collectorResult);
					return;
				}
			}
			Assert.fail("Right value not found. Got: " + collectorResult);
		}
	}

	@Test
	public void test910collector() throws URISyntaxException, IOException {
		final AnnotatedIndexService master = getMaster();
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery());
		builder.collector("minPrice", MinNumericCollector.MinDouble.class, "price");
		builder.collector("maxPrice", MaxNumericCollector.MaxDouble.class, "price");
		builder.collector("minQuantity", MinNumericCollector.MinLong.class, AnnotatedIndex.DV_QUANTITY_FIELD);
		builder.collector("maxQuantity", MaxNumericCollector.MaxLong.class, AnnotatedIndex.DV_QUANTITY_FIELD);
		ResultDefinition.WithObject<AnnotatedIndex> result = master.searchQuery(builder.build());
		checkCollector(result, "minPrice", 1.11d);
		checkCollector(result, "maxPrice", 2.22d);
		checkCollector(result, "minQuantity", 10L, 10);
		checkCollector(result, "maxQuantity", 20L, 20);
	}

	@Test
	public void test920ClassicCollector() throws URISyntaxException, IOException {
		final AnnotatedIndexService master = getMaster();
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery());
		builder.collector("maxQuantity", ClassicMaxCollector.class);
		ResultDefinition.WithObject<AnnotatedIndex> result = master.searchQuery(builder.build());
		checkCollector(result, "maxQuantity", 20L, 20);
	}

	private void checkFacets(ResultDefinition.WithObject<AnnotatedIndex> result, String facetName, String facetDim) {
		Assert.assertNotNull(result.facets);
		Map<String, Number> facets = result.facets.get(facetName);
		Assert.assertNotNull(facets);
		Assert.assertTrue(facets.containsKey(facetDim));
	}

	@Test
	public void test930ClassicCollectorWithDrillSideways() throws URISyntaxException, IOException {
		final AnnotatedIndexService master = getMaster();
		final QueryBuilder builder = QueryDefinition.of(
				new DrillDownQuery(new MatchAllDocsQuery(), true).add("dynamic_multi_facet_cat", "news"));
		builder.collector("maxQuantity", ClassicMaxCollector.class);
		builder.facet("dynamic_multi_facet_cat", new FacetDefinition(10));
		ResultDefinition.WithObject<AnnotatedIndex> result = master.searchQuery(builder.build());
		checkCollector(result, "maxQuantity", 20L, 20);
		checkFacets(result, "dynamic_multi_facet_cat", "news");
	}

	@Test
	public void test935ClassicCollectorWithFacets() throws URISyntaxException, IOException {
		final AnnotatedIndexService master = getMaster();
		final QueryBuilder builder = QueryDefinition.of(new MatchAllDocsQuery());
		builder.collector("maxQuantity", ClassicMaxCollector.class);
		builder.facet("dynamic_multi_facet_cat", new FacetDefinition(10));
		ResultDefinition.WithObject<AnnotatedIndex> result = master.searchQuery(builder.build());
		checkCollector(result, "maxQuantity", 20L, 20);
		checkFacets(result, "dynamic_multi_facet_cat", "news");
	}

	@Test
	public void test950DeleteAll() throws URISyntaxException, IOException {
		getMaster().deleteAll();
	}

	@Test
	public void test960DeleteBackup() throws URISyntaxException, IOException {
		SortedMap<String, SortedMap<String, SortedMap<String, BackupStatus>>> backups =
				getMaster().getBackups("*", true);
		Assert.assertNotNull(backups);
		Assert.assertFalse(backups.isEmpty());
		final Integer count = getMaster().deleteBackups(BACKUP_NAME);
		Assert.assertNotNull(count);
		Assert.assertEquals(1, count, 0);
		backups = getMaster().getBackups("*", true);
		Assert.assertNotNull(backups);
		Assert.assertTrue(backups.isEmpty());
	}

	protected abstract File getIndexDirectory();

	private File getSchemaDir() {
		return new File(getIndexDirectory(), AnnotatedIndex.SCHEMA_NAME);
	}

	private File getIndexDir(String name) {
		return new File(getSchemaDir(), name);
	}

	@Test
	public void test970checkUpdatableAnalyzers() throws IOException, URISyntaxException {
		IndexStatus indexStatus = getMaster().getIndexStatus();
		Assert.assertEquals(1, indexStatus.active_query_analyzers, 0);
		Assert.assertEquals(1, indexStatus.active_index_analyzers, 0);
		indexStatus = getSlave().getIndexStatus();
		Assert.assertEquals(1, indexStatus.active_query_analyzers, 0);
		Assert.assertEquals(1, indexStatus.active_index_analyzers, 0);
	}

	@Test
	public void test980DeleteIndex() throws URISyntaxException, IOException {
		File indexSlave = getIndexDir(AnnotatedIndex.INDEX_NAME_SLAVE);
		Assert.assertTrue(indexSlave.exists());
		getSlave().deleteIndex();
		checkErrorStatusCode(() -> getSlave().getIndexStatus(), 404);
		Assert.assertFalse(indexSlave.exists());

		File indexMaster = getIndexDir(AnnotatedIndex.INDEX_NAME_MASTER);
		Assert.assertTrue(indexMaster.exists());
		getMaster().deleteIndex();
		checkErrorStatusCode(() -> getMaster().getIndexStatus(), 404);
		Assert.assertFalse(indexMaster.exists());
	}

	@Test
	public void test990DeleteSchema() throws URISyntaxException, IOException {
		Assert.assertTrue(getSchemaDir().exists());
		getMaster().deleteSchema();
		Assert.assertFalse(getSchemaDir().exists());
	}

}

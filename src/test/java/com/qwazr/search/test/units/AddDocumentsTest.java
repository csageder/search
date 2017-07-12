/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.test.units;

import com.qwazr.search.index.QueryBuilder;
import com.qwazr.search.index.QueryDefinition;
import com.qwazr.search.index.ResultDefinition;
import com.qwazr.search.query.MatchAllDocsQuery;
import com.qwazr.utils.RandomUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AddDocumentsTest extends AbstractIndexTest.WithIndexRecord {

	final private static String[] ID_FIELDS = { "1", "2", "3" };
	final private static String[] STORED_FIELDS = { "doc1", "doc2", "doc3" };
	final private static String[] SDV_FIELDS = { "sdv1", "sdv2", "sdv3" };
	final private static Double[] DDV_FIELDS = { 1.11d, 2.22d, 3.33d };
	final private static String[][] MULTI_STRING_STORED_FIELDS =
			{ { "s01", "s02", "s03" }, { "s11", "s12", "s13" }, { "s21", "s22", "s23" } };
	final private static Integer[][] MULTI_INTEGER_STORED_FIELDS = { { 11, 12, 13 }, { 21, 22, 23 }, { 31, 32, 33 } };

	@BeforeClass
	public static void setup() throws IOException, InterruptedException, URISyntaxException {
		initIndexService();
		for (int i = 0; i < ID_FIELDS.length; i++)
			indexService.postDocument(new IndexRecord(ID_FIELDS[i]).storedField(STORED_FIELDS[i])
					.sortedDocValue(SDV_FIELDS[i])
					.doubleDocValue(DDV_FIELDS[i])
					.multivaluedStringStoredField(MULTI_STRING_STORED_FIELDS[i])
					.multivaluedIntegerStoredField(MULTI_INTEGER_STORED_FIELDS[i]));
	}

	IndexRecord getSingleDoc(int pos) {
		return new IndexRecord(ID_FIELDS[pos]).storedField(STORED_FIELDS[pos])
				.sortedDocValue(SDV_FIELDS[pos])
				.doubleDocValue(DDV_FIELDS[pos])
				.multivaluedStringStoredField(MULTI_STRING_STORED_FIELDS[pos])
				.multivaluedIntegerStoredField(MULTI_INTEGER_STORED_FIELDS[pos]);
	}

	Collection<IndexRecord> getDocCollection() {
		final List<IndexRecord> records = new ArrayList<>();
		for (int i = 0; i < ID_FIELDS.length; i++)
			records.add(getSingleDoc(i));
		return records;
	}

	private ResultDefinition.WithObject<IndexRecord> withRecord(QueryBuilder queryBuilder, int expectedSize) {
		final ResultDefinition.WithObject<IndexRecord> result = indexService.searchQuery(queryBuilder.build());
		Assert.assertNotNull(result.total_hits);
		Assert.assertEquals(expectedSize, result.total_hits, 0);
		return result;
	}

	private ResultDefinition.WithMap withMap(QueryBuilder queryBuilder, int expectedSize) {
		final ResultDefinition.WithMap result = indexService.searchQueryWithMap(queryBuilder.build());
		Assert.assertNotNull(result.total_hits);
		Assert.assertEquals(expectedSize, result.total_hits, 0);
		return result;
	}

	@Before
	public void beforeTest() {
		indexService.deleteAll();
	}

	private QueryBuilder builder() {
		return QueryDefinition.of(new MatchAllDocsQuery()).start(0).rows(ID_FIELDS.length * 2);
	}

	private void checkResult(int expectedSize) {
		final QueryBuilder builder = builder().returnedField("*");
		withRecord(builder, expectedSize).forEach(doc -> {
			Assert.assertEquals(ID_FIELDS[doc.pos], doc.record.id);
			Assert.assertEquals(STORED_FIELDS[doc.pos], doc.record.storedField);
			Assert.assertEquals(SDV_FIELDS[doc.pos], doc.record.sortedDocValue);
			Assert.assertEquals(DDV_FIELDS[doc.pos], doc.record.doubleDocValue);
		});

		withMap(builder, expectedSize).forEach(doc -> {
			Assert.assertEquals(ID_FIELDS[doc.pos], doc.fields.get("$id$"));
			Assert.assertEquals(STORED_FIELDS[doc.pos], doc.fields.get("storedField"));
			Assert.assertEquals(SDV_FIELDS[doc.pos], doc.fields.get("sortedDocValue"));
			Assert.assertEquals(DDV_FIELDS[doc.pos], doc.fields.get("doubleDocValue"));
		});
	}

	private Map<String, String> getCommitData() {
		final Map<String, String> map = new HashMap<>();
		map.put(RandomUtils.alphanumeric(10), RandomUtils.alphanumeric(10));
		return map;
	}

	@Test
	public void addDocument() throws IOException, InterruptedException {
		indexService.addDocument(getSingleDoc(0));
		checkResult(1);
	}

	@Test
	public void addDocumentWithCommit() throws IOException, InterruptedException {
		final Map<String, String> commitData = getCommitData();
		indexService.addDocument(getSingleDoc(0), commitData);
		checkResult(1);
		Assert.assertTrue(Objects.deepEquals(commitData, indexService.getIndexStatus().commit_user_data));
	}

	@Test
	public void addDocuments() throws IOException, InterruptedException {
		indexService.addDocuments(getDocCollection());
		checkResult(3);
	}

	@Test
	public void addDocumentsWithCommit() throws IOException, InterruptedException {
		final Map<String, String> commitData = getCommitData();
		indexService.addDocuments(getDocCollection(), commitData);
		checkResult(3);
		Assert.assertTrue(Objects.deepEquals(commitData, indexService.getIndexStatus().commit_user_data));
	}

}

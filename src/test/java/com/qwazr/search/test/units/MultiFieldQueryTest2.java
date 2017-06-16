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
package com.qwazr.search.test.units;

import com.qwazr.search.index.QueryContext;
import com.qwazr.search.index.QueryDefinition;
import com.qwazr.search.query.AbstractQuery;
import com.qwazr.search.query.MultiFieldQuery2;
import com.qwazr.search.query.QueryParser;
import com.qwazr.search.query.QueryParserOperator;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Query;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

public class MultiFieldQueryTest2 extends AbstractIndexTest {

	@BeforeClass
	public static void setup() throws IOException, InterruptedException, URISyntaxException, ParseException {
		initIndexManager();
		indexManager.registerConstructorParameter(SynonymMap.class,
				RealTimeSynonymsResourcesTest.getSynonymMap(RealTimeSynonymsResourcesTest.WHITESPACE_ANALYZER,
						RealTimeSynonymsResourcesTest.EN_FR_DE_SYNONYMS));
		initIndexService();
		indexService.postDocument(new IndexRecord("1").textField("Hello World")
				.stringField("Hello World")
				.textSynonymsField1("hello world"));
		indexService.postDocument(new IndexRecord("2").textField("aaaaaa bbbbbb").stringField("aaaaaa bbbbbb"));
	}

	@Test
	public void testWithDefaultAnalyzer() {
		QueryDefinition queryDef;

		queryDef = QueryDefinition.of(new MultiFieldQuery2(QueryParserOperator.OR, "Hello", 0).boost("textField", 1F)
				.boost("stringField", 1F)).queryDebug(true).build();
		checkQuery(queryDef, 1L, "(textField:hello stringField:Hello~2)");

		queryDef = QueryDefinition.of(new MultiFieldQuery2(QueryParserOperator.AND, "Hello", 0).boost("textField", 1F)
				.boost("stringField", 1F)).queryDebug(true).build();
		checkQuery(queryDef, 1L, "(textField:hello stringField:Hello~2) #((textField:hello stringField:Hello~2)~1)");

		queryDef = QueryDefinition.of(new MultiFieldQuery2(QueryParserOperator.OR, "Hello world", 0).boost("textField",
				2F).boost("stringField", 1F)).queryDebug(true).build();
		checkQuery(queryDef, 1L, "textField:hello textField:world stringField:Hello world~2");

		queryDef = QueryDefinition.of(new MultiFieldQuery2(QueryParserOperator.AND, "Hello world", 0).boost("textField",
				2F).boost("stringField", 1F)).queryDebug(true).build();
		checkQuery(queryDef, 1L,
				"((textField:hello textField:world)^2.0 stringField:Hello world~2) #textField:hello #textField:world #stringField:Hello world~2");
	}

	@Test
	public void testWithMinShouldMatch() {
		QueryDefinition queryDef = QueryDefinition.of(new MultiFieldQuery2(QueryParserOperator.OR, "Hello world aaaaaa",
				2).boost("textField", 2F).boost("stringField", 1F)).queryDebug(true).build();
		checkQuery(queryDef, 1L,
				"(textField:hello textField:world textField:aaaaaa stringField:Hello world aaaaaa~2)~2");
	}

	@Test
	public void testWithCustomAnalyzer() {
		QueryDefinition queryDef;
		Analyzer analyzer = new StandardAnalyzer();

		queryDef = QueryDefinition.of(new MultiFieldQuery2(QueryParserOperator.OR, "Hello", 0, null, analyzer).boost(
				"textField", 1F).boost("stringField", 1F)).queryDebug(true).build();
		checkQuery(queryDef, 1L, "(textField:hello stringField:hello)");

		queryDef = QueryDefinition.of(new MultiFieldQuery2(QueryParserOperator.AND, "Hello", 0, null, analyzer).boost(
				"textField", 1F).boost("stringField", 1F)).queryDebug(true).build();
		checkQuery(queryDef, 1L, "(textField:hello stringField:hello) #((textField:hello stringField:hello)~1)");

		queryDef = QueryDefinition.of(new MultiFieldQuery2(QueryParserOperator.AND, "Hello zzzzz", 0, null,
				analyzer).boost("textField", 1F).boost("stringField", 1F)).queryDebug(true).build();
		checkQuery(queryDef, 0L,
				"((textField:hello textField:zzzzz~2) (stringField:hello stringField:zzzzz~2)) #((textField:hello stringField:hello)~1) #((textField:zzzzz~2 stringField:zzzzz~2)~1)");
	}

	@Test
	public void luceneQuery() throws IOException, ReflectiveOperationException {
		Query luceneQuery = new MultiFieldQuery2(QueryParserOperator.AND, "Hello World", 0).boost("textField", 1F)
				.boost("stringField", 1F)
				.getQuery(QueryContext.DEFAULT);
		Assert.assertNotNull(luceneQuery);
	}

	@Test
	public void testWithGraphSynonymsOperatorOrKeywordsIsOneMultiWordSynonym()
			throws QueryNodeException, ReflectiveOperationException,
			org.apache.lucene.queryparser.classic.ParseException, IOException {
		AbstractQuery query = new MultiFieldQuery2(QueryParserOperator.OR, "bonjour le monde").boost(
				"textSynonymsField1", 1.0F).boost("textField", 2.0F).boost("stringField", 3.0F);
		checkQuery(QueryDefinition.of(query).queryDebug(true).build());
	}

	@Test
	public void testWithGraphSynonymsOperatorOrKeywordsIsContainsMultiWordSynonym()
			throws QueryNodeException, ReflectiveOperationException,
			org.apache.lucene.queryparser.classic.ParseException, IOException {
		AbstractQuery query = new MultiFieldQuery2(QueryParserOperator.OR, "hello bonjour le monde").boost(
				"textSynonymsField1", 1.0F).boost("textField", 2.0F).boost("stringField", 3.0F);
		checkQuery(QueryDefinition.of(query).queryDebug(true).build());
	}

	@Test
	@Ignore
	public void testWithGraphSynonymsOperatorAndKeywordsIsOneMultiWordSynonym()
			throws QueryNodeException, ReflectiveOperationException,
			org.apache.lucene.queryparser.classic.ParseException, IOException {
		AbstractQuery query = new MultiFieldQuery2(QueryParserOperator.AND, "bonjour le monde").boost(
				"textSynonymsField1", 1.0F).boost("textField", 2.0F).boost("stringField", 3.0F);
		checkQuery(QueryDefinition.of(query).queryDebug(true).build(), 1L, "test");
	}

	@Test
	public void testWithGraphSynonymsOperatorAndKeywordsContainsMultiWordSynonymLast()
			throws QueryNodeException, ReflectiveOperationException,
			org.apache.lucene.queryparser.classic.ParseException, IOException {
		AbstractQuery query = QueryParser.of("textSynonymsField1")
				.setDefaultOperator(QueryParserOperator.AND)
				.setSplitOnWhitespace(false)
				.setQueryString("hello bonjour le monde")
				.build();
		checkQuery(QueryDefinition.of(query).queryDebug(true).build(), 1L,
				"+textSynonymsField1:hello +((+textSynonymsField1:hello +textSynonymsField1:world) (+textSynonymsField1:bonjour +textSynonymsField1:le +textSynonymsField1:monde))");
	}

	@Test
	public void testWithGraphSynonymsOperatorAndKeywordsContainsMultiWordSynonymFirst()
			throws QueryNodeException, ReflectiveOperationException,
			org.apache.lucene.queryparser.classic.ParseException, IOException {
		AbstractQuery query = QueryParser.of("textSynonymsField1")
				.setDefaultOperator(QueryParserOperator.AND)
				.setSplitOnWhitespace(false)
				.setQueryString("bonjour le monde hello")
				.build();
		checkQuery(QueryDefinition.of(query).queryDebug(true).build(), 1L,
				"+((+textSynonymsField1:hello +textSynonymsField1:world) (+textSynonymsField1:bonjour +textSynonymsField1:le +textSynonymsField1:monde)) +textSynonymsField1:hello");
	}

}

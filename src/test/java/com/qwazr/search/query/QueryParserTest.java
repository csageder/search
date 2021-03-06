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
package com.qwazr.search.query;

import com.qwazr.search.index.QueryContext;
import com.qwazr.search.index.QueryDefinition;
import com.qwazr.search.test.units.AbstractIndexTest;
import com.qwazr.search.test.units.IndexRecord;
import com.qwazr.search.test.units.RealTimeSynonymsResourcesTest;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Query;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;

public class QueryParserTest extends AbstractIndexTest.WithIndexRecord.NoTaxonomy {

	@BeforeClass
	public static void setup() throws IOException, InterruptedException, URISyntaxException, java.text.ParseException {
		initIndexManager();
		indexManager.registerConstructorParameter(SynonymMap.class,
				RealTimeSynonymsResourcesTest.getSynonymMap(RealTimeSynonymsResourcesTest.WHITESPACE_ANALYZER,
						RealTimeSynonymsResourcesTest.EN_FR_DE_SYNONYMS));
		initIndexService();
		indexService.postDocument(
				new IndexRecord.NoTaxonomy("1").textField("Hello world").textSynonymsField1("hello world"));
	}

	@Test
	public void testWithDefaultAnalyzer() {
		QueryDefinition queryDef = QueryDefinition.of(
				QueryParser.of("textField").setDefaultOperator(QueryParserOperator.AND).setQueryString("Hello").build())
				.build();
		checkQuery(queryDef);
	}

	@Test
	public void testWithCustomAnalyzer() {
		QueryDefinition queryDef = QueryDefinition.of(QueryParser.of("textField")
				.setDefaultOperator(QueryParserOperator.AND)
				.setQueryString("hello World")
				.setAnalyzer(new StandardAnalyzer())
				.build()).
				build();
		checkQuery(queryDef);
	}

	@Test
	public void luceneQuery() throws IOException, ReflectiveOperationException, ParseException, QueryNodeException {
		Query luceneQuery = QueryParser.of("textField")
				.setDefaultOperator(QueryParserOperator.AND)
				.setQueryString("Hello World")
				.build()
				.getQuery(QueryContext.DEFAULT);
		Assert.assertNotNull(luceneQuery);
	}

	@Test
	public void testWithGraphSynonymsOperatorOrKeywordsIsOneMultiWordSynonym()
			throws QueryNodeException, ReflectiveOperationException, ParseException, IOException {
		AbstractQuery query = QueryParser.of("textSynonymsField1")
				.setDefaultOperator(QueryParserOperator.OR)
				.setSplitOnWhitespace(false)
				.setQueryString("bonjour le monde")
				.build();
		checkQuery(QueryDefinition.of(query).queryDebug(true).build());
	}

	@Test
	public void testWithGraphSynonymsOperatorOrKeywordsIsContainsMultiWordSynonym()
			throws QueryNodeException, ReflectiveOperationException, ParseException, IOException {
		AbstractQuery query = QueryParser.of("textSynonymsField1")
				.setDefaultOperator(QueryParserOperator.OR)
				.setSplitOnWhitespace(false)
				.setQueryString("hello bonjour le monde")
				.build();
		checkQuery(QueryDefinition.of(query).queryDebug(true).build());
	}

	@Test
	public void testWithGraphSynonymsOperatorAndKeywordsIsOneMultiWordSynonym()
			throws QueryNodeException, ReflectiveOperationException, ParseException, IOException {
		AbstractQuery query = QueryParser.of("textSynonymsField1")
				.setDefaultOperator(QueryParserOperator.AND)
				.setSplitOnWhitespace(false)
				.setQueryString("bonjour le monde")
				.build();
		checkQuery(QueryDefinition.of(query).queryDebug(true).build());
	}

	@Test
	public void testWithGraphSynonymsOperatorAndKeywordsContainsMultiWordSynonymLast()
			throws QueryNodeException, ReflectiveOperationException, ParseException, IOException {
		AbstractQuery query = QueryParser.of("textSynonymsField1")
				.setDefaultOperator(QueryParserOperator.AND)
				.setSplitOnWhitespace(false)
				.setQueryString("hello bonjour le monde")
				.build();
		checkQuery(QueryDefinition.of(query).queryDebug(true).build());
	}

	@Test
	public void testWithGraphSynonymsOperatorAndKeywordsContainsMultiWordSynonymFirst()
			throws QueryNodeException, ReflectiveOperationException, ParseException, IOException {
		AbstractQuery query = QueryParser.of("textSynonymsField1")
				.setDefaultOperator(QueryParserOperator.AND)
				.setSplitOnWhitespace(false)
				.setQueryString("bonjour le monde hello")
				.build();
		checkQuery(QueryDefinition.of(query).queryDebug(true).build());
	}

}

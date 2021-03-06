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
package com.qwazr.search.field;

import com.qwazr.search.index.BytesRefUtils;
import com.qwazr.search.index.FieldConsumer;
import com.qwazr.utils.WildcardMatcher;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.facet.FacetField;

import java.util.Arrays;

final class FacetType extends StorableFieldType {

	FacetType(final String genericFieldName, final WildcardMatcher wildcardMatcher, final FieldDefinition definition) {
		super(of(genericFieldName, wildcardMatcher, (CustomFieldDefinition) definition).bytesRefConverter(
				BytesRefUtils.Converter.STRING));
	}

	@Override
	final protected void fillArray(final String fieldName, final String[] values, final FieldConsumer consumer) {
		consumer.accept(genericFieldName, fieldName, new FacetField(fieldName, values));
		if (store)
			consumer.accept(genericFieldName, fieldName, new StoredField(fieldName, Arrays.toString(values)));
	}

	private String getStringValue(Object value) {
		if (value == null)
			return null;
		final String stringValue = value.toString();
		return stringValue == null || stringValue.isEmpty() ? null : stringValue;
	}

	@Override
	void newFieldWithStore(String fieldName, Object value, FieldConsumer consumer) {
		final String stringValue = getStringValue(value);
		if (stringValue == null)
			return;
		consumer.accept(genericFieldName, fieldName, new FacetField(fieldName, stringValue));
		consumer.accept(genericFieldName, fieldName, new StoredField(fieldName, stringValue));
	}

	@Override
	void newFieldNoStore(String fieldName, Object value, FieldConsumer consumer) {
		final String stringValue = getStringValue(value);
		if (stringValue != null)
			consumer.accept(genericFieldName, fieldName, new FacetField(fieldName, stringValue));
	}
}

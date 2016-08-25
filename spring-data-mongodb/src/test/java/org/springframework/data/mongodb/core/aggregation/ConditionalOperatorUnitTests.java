/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core.aggregation;

import static org.junit.Assert.*;
import static org.springframework.data.mongodb.core.aggregation.ConditionalOperator.*;
import static org.springframework.data.mongodb.test.util.IsBsonObject.*;

import org.junit.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.test.util.BasicDbListBuilder;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Unit tests for {@link ConditionalOperator}.
 *
 * @author Mark Paluch
 */
public class ConditionalOperatorUnitTests {

	/**
	 * @see DATAMONGO-861
	 */
	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectNullCondition() {
		new ConditionalOperator((Field) null, "", "");
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectThenValue() {
		new ConditionalOperator(Fields.field("field"), null, "");
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectOtherwiseValue() {
		new ConditionalOperator(Fields.field("field"), "", null);
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test(expected = IllegalArgumentException.class)
	public void builderRejectsEmptyFieldName() {
		newBuilder().when("");
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test(expected = IllegalArgumentException.class)
	public void builderRejectsNullFieldName() {
		newBuilder().when((DBObject) null);
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test(expected = IllegalArgumentException.class)
	public void builderRejectsNullCriteriaName() {
		newBuilder().when((Criteria) null);
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test(expected = IllegalArgumentException.class)
	public void builderRejectsBuilderAsThenValue() {
		newBuilder().when("isYellow").then(newBuilder().when("field").then("then-value")).otherwise("otherwise");
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void simpleBuilderShouldRenderCorrectly() {

		ConditionalOperator operator = newBuilder().when("isYellow").then("bright").otherwise("dark");
		DBObject dbObject = operator.toDbObject(Aggregation.DEFAULT_CONTEXT);

		DBObject expectedCondition = new BasicDBObject() //
				.append("if", "$isYellow") //
				.append("then", "bright") //
				.append("else", "dark");

		assertThat(dbObject, isBsonObject().containing("$cond", expectedCondition));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void simpleCriteriaShouldRenderCorrectly() {

		ConditionalOperator operator = newBuilder().when(Criteria.where("luminosity").gte(100)).then("bright")
				.otherwise("dark");
		DBObject dbObject = operator.toDbObject(Aggregation.DEFAULT_CONTEXT);

		DBObject expectedCondition = new BasicDBObject() //
				.append("if", new BasicDBObject("$gte", new BasicDbListBuilder().add("$luminosity").add(100).get())) //
				.append("then", "bright") //
				.append("else", "dark");

		assertThat(dbObject, isBsonObject().containing("$cond", expectedCondition));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void andCriteriaShouldRenderCorrectly() {

		ConditionalOperator operator = newBuilder() //
				.when(Criteria.where("luminosity").gte(100) //
						.andOperator(Criteria.where("hue").is(50), //
								Criteria.where("saturation").lt(11)))
				.then("bright").otherwise("dark");

		DBObject dbObject = operator.toDbObject(Aggregation.DEFAULT_CONTEXT);

		BasicDBObject luminosity = new BasicDBObject("$gte", new BasicDbListBuilder().add("$luminosity").add(100).get());
		BasicDBObject hue = new BasicDBObject("$eq", new BasicDbListBuilder().add("$hue").add(50).get());
		BasicDBObject saturation = new BasicDBObject("$lt", new BasicDbListBuilder().add("$saturation").add(11).get());

		DBObject expectedCondition = new BasicDBObject() //
				.append("if",
						new BasicDbListBuilder().add(luminosity)
								.add(new BasicDBObject("$and", new BasicDbListBuilder().add(hue).add(saturation).get())).get()) //
				.append("then", "bright") //
				.append("else", "dark");

		assertThat(dbObject, isBsonObject().containing("$cond", expectedCondition));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void twoArgsCriteriaShouldRenderCorrectly() {

		Criteria criteria = Criteria.where("luminosity").gte(100) //
				.and("saturation").and("chroma").is(200);
		ConditionalOperator operator = newBuilder().when(criteria).then("bright").otherwise("dark");

		DBObject dbObject = operator.toDbObject(Aggregation.DEFAULT_CONTEXT);

		BasicDBObject gte = new BasicDBObject("$gte", new BasicDbListBuilder().add("$luminosity").add(100).get());
		BasicDBObject is = new BasicDBObject("$eq", new BasicDbListBuilder().add("$chroma").add(200).get());

		DBObject expectedCondition = new BasicDBObject() //
				.append("if", new BasicDbListBuilder().add(gte).add(is).get()) //
				.append("then", "bright") //
				.append("else", "dark");

		assertThat(dbObject, isBsonObject().containing("$cond", expectedCondition));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void nestedCriteriaShouldRenderCorrectly() {

		ConditionalOperator operator = newBuilder() //
				.when(Criteria.where("luminosity").gte(100)) //
				.then(newBuilder() //
						.when(Criteria.where("luminosity").gte(200)) //
						.then("verybright") //
						.otherwise("not-so-bright")) //
				.otherwise(newBuilder() //
						.when(Criteria.where("luminosity").lt(50)) //
						.then("very-dark") //
						.otherwise("not-so-dark"));

		DBObject dbObject = operator.toDbObject(Aggregation.DEFAULT_CONTEXT);

		DBObject trueCondition = new BasicDBObject() //
				.append("if", new BasicDBObject("$gte", new BasicDbListBuilder().add("$luminosity").add(200).get())) //
				.append("then", "verybright") //
				.append("else", "not-so-bright");

		DBObject falseCondition = new BasicDBObject() //
				.append("if", new BasicDBObject("$lt", new BasicDbListBuilder().add("$luminosity").add(50).get())) //
				.append("then", "very-dark") //
				.append("else", "not-so-dark");

		assertThat(dbObject, isBsonObject().containing("$cond.then.$cond", trueCondition));
		assertThat(dbObject, isBsonObject().containing("$cond.else.$cond", falseCondition));
	}
}

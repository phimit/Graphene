package org.lambda3.graphene.core;

/*-
 * ==========================License-Start=============================
 * GrapheneTest.java - Graphene Core - Lambda^3 - 2017
 * Graphene
 * %%
 * Copyright (C) 2017 Lambda^3
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * ==========================License-End===============================
 */


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.lambda3.graphene.core.relation_extraction.formatter.FormatterFactory;
import org.lambda3.text.simplification.discourse.model.SimplificationContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class GrapheneTest {
	private final static Logger LOG = LoggerFactory.getLogger(GrapheneTest.class);

	private static Graphene graphene;

	@BeforeClass
	public static void beforeAll() {
		Config config = ConfigFactory
			.load("reference.local")
			.withFallback(ConfigFactory.load("reference"));

		graphene = new Graphene();
	}

	@Test
	public void testSerializationAndDeserialization() throws IOException {
		String text = "Although the Treasury will announce details of the November refunding on Monday, the funding will be delayed if Congress and President Bush fail to increase the Treasury's borrowing capacity.";
		SimplificationContent c = graphene.doRelationExtraction(text, false, false, true);

		File temp = File.createTempFile("graphene-test", ".json");
		temp.deleteOnExit();

		LOG.info("SAVE TO FILE...");
		c.serializeToJSON(temp);

		LOG.info("LOAD FROM FILE...");
		SimplificationContent loaded = SimplificationContent.deserializeFromJSON(temp, SimplificationContent.class);

		LOG.info(FormatterFactory.get("default").format(loaded.getSentences(), false));
	}

	@Test
	public void testOutput() throws IOException {
		String text = "Although the Treasury will announce details of the November refunding on Monday, the funding will be delayed if Congress and President Bush fail to increase the Treasury's borrowing capacity.";
		SimplificationContent c = graphene.doRelationExtraction(text, false, false, false);

		LOG.info(FormatterFactory.get("default").format(c.getSentences(), false));
		LOG.info(FormatterFactory.get("flat").format(c.getSentences(), false));
		LOG.info(FormatterFactory.get("rdf").format(c.getSentences(), false));
	}
}

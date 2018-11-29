package org.lambda3.graphene.cli;

/*-
 * ==========================License-Start=============================
 * GrapheneCLI.java - Graphene CLI - Lambda^3 - 2017
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


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.lambda3.graphene.core.Graphene;
import org.lambda3.graphene.core.coreference.model.CoreferenceContent;
import org.lambda3.graphene.core.relation_extraction.formatter.FormatterFactory;
import org.lambda3.text.simplification.discourse.model.Content;
import org.lambda3.text.simplification.discourse.model.SimplificationContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GrapheneCLI {

	private final static Logger LOG = LoggerFactory.getLogger(GrapheneCLI.class);

	private static final ObjectWriter JSON = new ObjectMapper().writerWithDefaultPrettyPrinter();

	private final CmdLineParser cli;

	@Option(name = "--help", aliases = {"-h"}, usage = "Prints help")
	private boolean help = false;

	@Option(name = "--version", aliases = {"-v"}, usage = "Prints the version of Graphene")
	private boolean version = false;

	@Option(name = "--operation", usage = "Choose whether to run Coreference-Resolution [COREF], Discourse-Simplification [SIM], or Relation-Extraction [RE].")
	private Operation operation;

	@Option(name = "--input", usage = "Choose the input format [TEXT/FILE/WIKI]")
	private InputSource inputSource;

	@Option(name = "--output", usage = "Choose whether to create files [FILE] or print result to commandline [CMDLINE].")
	private OutputSource outputSource;

	@Option(name = "--corefformat",
		usage = "Specifies which textual representation for Coreference-Resolution should be returned [DEFAULT/SERIALIZED].")
	private CoreferenceResolutionOutputFormat corefOutputFormat = CoreferenceResolutionOutputFormat.DEFAULT;

	@Option(name = "--simformat",
		usage = "Specifies which textual representation for Discourse-Simplification should be returned [DEFAULT/DEFAULT_RESOLVED/FLAT/FLAT_RESOLVED/SERIALIZED].")
	private DiscourseSimplificationOutputFormat simOutputFormat = DiscourseSimplificationOutputFormat.DEFAULT;

	@Option(name = "--reformat",
		usage = "Specifies which textual representation for Relation-Extraction should be returned [DEFAULT/DEFAULT_RESOLVED/FLAT/FLAT_RESOLVED/RDF/SERIALIZED].")
	private RelationExtractionOutputFormat reOutputFormat = RelationExtractionOutputFormat.DEFAULT;

	@Option(name = "--doCoreference",
		usage = "Specifies whether coreference should be executed before Discourse-Simplification or Relation-Extraction.")
	private boolean doCoref = false;

	@Option(name = "--doComplexCategories",
		usage = "Specifies whether complex categories should be extracted.")
	private boolean doComplexCategoryExtraction = false;

	@Option(name = "--isolateSentences",
		usage = "Specifies whether the sentences from the input text should be processed individually (This will not extract relationships that occur between neighboured sentences). Set true, if you run Graphene over a collection of independent sentences and false for a full coherent text.")
	private boolean isolateSentences = false;

	@Argument(usage = "Input texts/files/articles")
	private List<String> input;

	private GrapheneCLI() {
		this.cli = new CmdLineParser(this);
	}

	public static void main(String[] args) {
		new GrapheneCLI().doMain(args);
	}

	private static List<String> getInput(List<String> input, InputSource format) {

		List<String> result = null;

		switch (format) {
			case TEXT:
				result = input;
				break;
			case FILE:
				result = input.stream().map(GrapheneCLI::readFromFile).collect(Collectors.toList());
				break;
			case WIKI:
				throw new IllegalArgumentException("This is not (yet) implemented.");
		}

		return result;
	}

	private static String readFromFile(String filename) {

		final StringBuilder sb = new StringBuilder();

		try (BufferedReader br = Files.newBufferedReader(new File(filename).toPath())) {
			br.lines().forEach(l -> sb.append(l).append(" "));
		} catch (IOException e) {
			LOG.warn("Can't read from file.", e);
		}

		return sb.toString();
	}

	private static void printHelp() {
		// TODO: Print help about all usage.
		System.out.println("Give the configuration with -Dconfig.file=<file_name>");
	}

	private static void printVersion(Graphene graphene) {
		System.out.print("Graphene VersionInfo: ");
		try {
			System.out.println(JSON.writeValueAsString(graphene.getVersionInfo()));
		} catch (JsonProcessingException e) {
			LOG.error("Cannot convert VersionInfo to JSON", e);
		}
	}

	private void doMain(String[] args) {
		try {
			cli.parseArgument(args);
		} catch (CmdLineException e) {
			inputError(e.getMessage(), true);
			return;
		}

		if (help) {
			printHelp();
			return;
		}

		// Init Graphene
		Config config = ConfigFactory.load()
			.withFallback(ConfigFactory.load("reference-core"))
			.withFallback(ConfigFactory.load("application-cli"));
		Graphene graphene = new Graphene(config);

		if (version) {
			printVersion(graphene);
			return;
		}

		if (input == null || input.size() == 0) {
			inputError("Input must be at least one entry.", false);
		}

		List<String> inputTexts = getInput(input, inputSource);

		Optional<List<Content>> result = Optional.empty();

		switch (operation) {
			case COREF:
				result = Optional.of(
					inputTexts
						.stream()
						.map(graphene::doCoreference)
						.collect(Collectors.toList()));
				break;
			case SIM:
				result = Optional.of(
					inputTexts
						.stream()
						.map(text -> graphene.doDiscourseSimplification(text, doCoref, isolateSentences))
						.collect(Collectors.toList()));
				break;
			case RE:
				result = Optional.of(
					inputTexts
						.stream()
						.map(text -> graphene.doRelationExtraction(text, doCoref, isolateSentences, doComplexCategoryExtraction))
						.collect(Collectors.toList()));
				break;
			default:
				throw new AssertionError("Unknown Operation");
		}

		result.orElseThrow(() -> new IllegalArgumentException("No valid configuration"));
		result.ifPresent(this::printOrWriteResult);
	}

	private List<Result> convertContents(List<Content> contents) {

		List<Result> results = new ArrayList<>();

		StringBuilder outputName = new StringBuilder();
		outputName.append("output_");

		switch (operation) {
			case COREF:
				outputName.append("coref_");
				break;
			case SIM:
				outputName.append("sim_");
				if (doCoref) {
					outputName.append("coref_");
				}
				break;
			case RE:
				outputName.append("re_");
				if (doCoref) {
					outputName.append("coref_");
				}
				break;
			default:
				throw new AssertionError("Unknown Operation");
		}

		for (int i = 0; i < contents.size(); ++i) {

			switch (inputSource) {
				case TEXT:
					outputName.append(String.format("%0" + input.size() + "d", i + 1));
					break;
				case FILE:
					outputName.append(Paths.get(input.get(i)).getFileName().toString().replace("\\s+", "-"));
					break;
				case WIKI:
					outputName.append(input.get(i).replace("\\s+", "-"));
					break;
			}

			results.add(new Result(outputName.toString(), contents.get(i)));
		}

		return results;
	}

	private void printOrWriteResult(List<Content> contents) {

		if (contents.size() != input.size()) {
			LOG.error("The output length is not the same as the input size: {}:{}", contents.size(), input.size());
		}

		switch (outputSource) {
			case CMDLINE:
				convertContents(contents).forEach(this::printResult);
				break;
			case FILE:
				convertContents(contents).forEach(this::writeResult);
				break;
		}
	}

	private String format(Content content) throws JsonProcessingException {

		if (content instanceof CoreferenceContent) {
			CoreferenceContent c = (CoreferenceContent)content;
			switch (corefOutputFormat) {
				case DEFAULT:
					return c.getSubstitutedText();
				case SERIALIZED:
					return content.prettyPrintJSON();
			}
		}

		if (content instanceof SimplificationContent) {
			SimplificationContent c = (SimplificationContent)content;
			if (operation.equals(Operation.RE)) {
				switch (reOutputFormat) {
					case DEFAULT:
						return FormatterFactory.get("default").format(c.getSentences(), false);
					case DEFAULT_RESOLVED:
						return FormatterFactory.get("default").format(c.getSentences(), true);
					case FLAT:
						return FormatterFactory.get("flat").format(c.getSentences(), false);
					case FLAT_RESOLVED:
						return FormatterFactory.get("flat").format(c.getSentences(), true);
					case RDF:
						return FormatterFactory.get("rdf").format(c.getSentences(), true);
					case SERIALIZED:
						return content.prettyPrintJSON();
				}
			} else if (operation.equals(Operation.SIM)) {
				switch (simOutputFormat) {
					case DEFAULT:
						return c.defaultFormat(false);
					case DEFAULT_RESOLVED:
						return c.defaultFormat(true);
					case FLAT:
						return c.flatFormat(false);
					case FLAT_RESOLVED:
						return c.flatFormat(true);
					case SERIALIZED:
						return content.prettyPrintJSON();
				}
			}
		}

		return content.prettyPrintJSON();
	}

	private void printResult(Result result) {

		StringBuilder sb = new StringBuilder();

		sb.append("############\n");
		sb.append("Name: ").append(result.getName()).append(" →\n");

		try {
			sb.append(format(result.getContent()));
		} catch (JsonProcessingException e) {
			LOG.error("Could not convert the result of '{}' to JSON", result.getName());
			LOG.info("Exception:", e);
			return; // error, we have nothing to print
		}

		System.out.println(sb.toString());

	}

	private void writeResult(Result result) {
		File outfile = new File(String.format("%s.txt", result.getName()));
		try (FileWriter fw = new FileWriter(outfile)) {

			fw.write(format(result.getContent()));
		} catch (FileNotFoundException e) {
			LOG.error("Could not write file, file not found!", e);
		} catch (IOException e) {
			LOG.error("Could not write file", e);
		}
	}

	private void inputError(String message, boolean withUsage) {
		System.err.println(message);
		if (withUsage) {
			cli.printUsage(System.err);
			System.err.println();
		}
	}

	private enum Operation {
		COREF,
		SIM,
		RE
	}

	private enum OutputSource {
		CMDLINE,
		FILE
	}

	private enum InputSource {
		TEXT,
		FILE,
		WIKI
	}

	private enum CoreferenceResolutionOutputFormat {
		DEFAULT,
		SERIALIZED
	}

	private enum DiscourseSimplificationOutputFormat {
		DEFAULT,
		DEFAULT_RESOLVED,
		FLAT,
		FLAT_RESOLVED,
		SERIALIZED
	}

	private enum RelationExtractionOutputFormat {
		DEFAULT,
		DEFAULT_RESOLVED,
		FLAT,
		FLAT_RESOLVED,
		RDF,
		SERIALIZED
	}
}

package io.github.jollygood1.syntaxthemes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a TextMate theme (the JSON format TM4E reads) from a palette, for themes that do not bring one.
 * TM4E colours the Generic Code Editor (Vue, TypeScript, YAML, SCSS ...), which the editor preferences
 * do not reach. Each row below gives TextMate scopes the colour of one palette key.
 */
final class TextMateGenerator {

	/** palette key, font style or null, scopes... */
	private static final String[][] RULES = {
			{ "singleLineComment", null, "comment", "punctuation.definition.comment" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "keyword", null, "keyword", "storage", "storage.type", "storage.modifier", "keyword.control", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
					"keyword.other.important.css" }, //$NON-NLS-1$
			{ "operator", null, "keyword.operator", "punctuation.separator.key-value", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"punctuation.separator.dictionary.key-value.json" }, //$NON-NLS-1$
			{ "string", null, "string", "punctuation.definition.string", "string.quoted", "string.template" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			{ "number", null, "constant.numeric", "constant.language", "constant.character", "constant.other", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					"support.constant", "keyword.other.unit", "variable.other.constant", "constant.language.json" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			{ "localVariable", null, "variable", "variable.parameter", "variable.other.readwrite", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"meta.object-literal.key", "variable.other.property" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "method", null, "entity.name.function", "support.function", "meta.function-call", "entity.name.method" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			{ "class", null, "entity.name.type", "entity.name.class", "support.class", "entity.other.inherited-class", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					"support.type", "entity.name.type.class", "entity.name.namespace" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "localVariableDeclaration", null, "entity.name.tag", "punctuation.definition.tag" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "localVariableDeclaration", null, "entity.name.tag.vue", "entity.name.tag.template", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"entity.name.tag.script", "entity.name.tag.style" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "field", null, "entity.other.attribute-name", "entity.other.attribute-name.html", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"entity.other.attribute-name.vue", "punctuation.attribute-shorthand.bind", //$NON-NLS-1$ //$NON-NLS-2$
					"punctuation.attribute-shorthand.event" }, //$NON-NLS-1$
			{ "foreground", null, "punctuation.definition.tag", "punctuation.definition.tag.begin", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"punctuation.definition.tag.end" }, //$NON-NLS-1$
			{ "keyword", null, "punctuation.definition.template-expression", "punctuation.section.embedded", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"meta.interpolationpunctuation" }, //$NON-NLS-1$
			{ "class", null, "entity.other.attribute-name.class.css", "entity.other.attribute-name.id.css", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"entity.name.tag.css" }, //$NON-NLS-1$
			{ "foreground", null, "support.type.property-name.css", "support.type.vendored.property-name.css" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "number", null, "support.constant.property-value.css", "constant.other.color" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "localVariable", null, "support.type.property-name.json", "support.type.property-name" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "method", "bold", "markup.heading" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "number", "bold", "markup.bold" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "keyword", "italic", "markup.italic" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{ "localVariable", "underline", "invalid", "invalid.illegal" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			{ "singleLineComment", "strikethrough", "invalid.deprecated" } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private TextMateGenerator() {
	}

	/** Writes the theme next to the imported themes (configuration area) and returns the file. */
	static Path write(ColorTheme theme) throws IOException {
		Path dir = ThemeRegistry.importDir().resolveSibling("textmate"); //$NON-NLS-1$
		Files.createDirectories(dir);
		Path file = dir.resolve(theme.getName().replaceAll("[^A-Za-z0-9._ -]", "_") + ".json"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Files.writeString(file, toJson(theme), StandardCharsets.UTF_8);
		return file;
	}

	static String toJson(ColorTheme theme) {
		String fg = theme.hex("foreground"); //$NON-NLS-1$
		String bg = theme.hex("background"); //$NON-NLS-1$
		StringBuilder sb = new StringBuilder();
		sb.append("{\n  \"name\": ").append(quote(theme.getName())); //$NON-NLS-1$
		sb.append(",\n  \"type\": \"").append(theme.isDark() ? "dark" : "light").append("\",\n  \"colors\": {\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		sb.append("    \"editor.background\": \"").append(bg).append("\",\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("    \"editor.foreground\": \"").append(fg).append('"'); //$NON-NLS-1$
		color(sb, "editor.selectionBackground", theme.hex("selectionBackground")); //$NON-NLS-1$ //$NON-NLS-2$
		color(sb, "editor.selectionForeground", theme.hex("selectionForeground")); //$NON-NLS-1$ //$NON-NLS-2$
		color(sb, "editor.lineHighlightBackground", theme.hex("currentLine")); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("\n  },\n  \"tokenColors\": [\n"); //$NON-NLS-1$
		sb.append("    { \"settings\": { \"background\": \"").append(bg).append("\", \"foreground\": \"").append(fg) //$NON-NLS-1$ //$NON-NLS-2$
				.append("\" } }"); //$NON-NLS-1$
		for (String[] rule : RULES) {
			String hex = theme.hex(rule[0]);
			if (hex == null) {
				hex = fg; // a palette without this key: plain text colour
			}
			sb.append(",\n    { \"scope\": ["); //$NON-NLS-1$
			for (int i = 2; i < rule.length; i++) {
				sb.append(i > 2 ? ", " : "").append(quote(rule[i])); //$NON-NLS-1$ //$NON-NLS-2$
			}
			sb.append("], \"settings\": { \"foreground\": \"").append(hex).append('"'); //$NON-NLS-1$
			String fontStyle = rule[1];
			if (fontStyle == null) {
				// the palette's own bold / italic / underline / strikethrough for this key
				StringBuilder words = new StringBuilder();
				for (String style : ColorTheme.STYLES) {
					if (Boolean.TRUE.equals(theme.style(rule[0], style))) {
						words.append(words.length() > 0 ? " " : "").append(style); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
				fontStyle = words.length() > 0 ? words.toString() : null;
			}
			if (fontStyle != null) {
				sb.append(", \"fontStyle\": \"").append(fontStyle).append('"'); //$NON-NLS-1$
			}
			sb.append(" } }"); //$NON-NLS-1$
		}
		return sb.append("\n  ]\n}\n").toString(); //$NON-NLS-1$
	}

	private static void color(StringBuilder sb, String key, String hex) {
		if (hex != null) {
			sb.append(",\n    \"").append(key).append("\": \"").append(hex).append('"'); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static String quote(String s) {
		return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"'; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}
}

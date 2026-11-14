# Generates mappings/*.xml for the plugin from the compact tables below.
# Run:  python -I tools/gen_mappings.py   (from the repository root)
# The pluginKey -> themeKey pairs follow the open-source Eclipse Color Theme plugin (EPL-1.0)
# and were compared with its mapping files on 2026-11-14;
# the WST JSON table is new. Edit the tables, run again, rebuild.
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                   "io.github.jollygood1.syntaxthemes", "mappings")
WST = "{hex} | null | null | null | null | null"
WST_NOITALIC = "{hex} | null | null | false | null | null"
RGB = "{rgb}"
HEX = "{hex}"


def pairs(text):
    out = []
    for line in text.strip().splitlines():
        parts = line.split()
        if not parts:
            continue
        key, theme = parts[0], parts[1]
        out.append((key, theme, parts[2] if len(parts) > 2 else None))
    return out


JAVA_COMMON = """
java_bracket bracket
java_comment_task_tag commentTaskTag
java_default foreground
java_doc_default javadoc
java_doc_keyword javadocKeyword
java_doc_link javadocLink
java_doc_tag javadocTag
java_keyword keyword
java_keyword_return keyword
java_multi_line_comment multiLineComment
java_operator operator
java_single_line_comment singleLineComment
java_string string
"""

PHP_COMMON = """
codeStyleNormal foreground
editorColorBoundarymaker foreground
editorColorComment multiLineComment NI
editorColorHeredoc javadoc NI
editorColorKeyword keyword
editorColorLineComment singleLineComment NI
editorColorNumber number
editorColorPHPDocComment javadoc NI
editorColorPhpdoc javadoc NI
editorColorString string
editorColorTask commentTaskTag NI
editorColorVariable localVariable
"""

PHP_SEM = "semanticHighlighting.org.eclipse.php.internal.ui.editor.highlighters."

MAPPINGS = {
    "org.eclipse.ui.editors": (RGB, """
AbstractTextEditor.Color.Background background
AbstractTextEditor.Color.FindScope findScope
AbstractTextEditor.Color.Foreground foreground
AbstractTextEditor.Color.SelectionBackground selectionBackground
AbstractTextEditor.Color.SelectionForeground selectionForeground
PHPReadOccurrenceIndicationColor occurrenceIndication
PHPWriteOccurrenceIndicationColor writeOccurrenceIndication
asOccurencesIndicationColor occurrenceIndication
currentLineColor currentLine
deletionIndicationColor deletionIndication
filteredSearchResultIndicationColor filteredSearchResultIndication
javaScriptOccurrenceIndicationColor occurrenceIndication
lineNumberColor lineNumber
matchingTagIndicationColor occurrenceIndication
occurrenceIndicationColor occurrenceIndication
org.eclipse.cdt.ui.occurrenceIndicationColor occurrenceIndication
printMarginColor lineNumber
pydevOccurrenceIndicationColor occurrenceIndication
searchResultIndicationColor searchResultIndication
writeOccurrenceIndicationColor writeOccurrenceIndication
currentIPColor debugCurrentInstructionPointer
secondaryIPColor debugSecondaryInstructionPointer
""", {
        "AbstractTextEditor.Color.Background.SystemDefault": "false",
        "AbstractTextEditor.Color.Foreground.SystemDefault": "false",
        "AbstractTextEditor.Color.SelectionBackground.SystemDefault": "false",
        "AbstractTextEditor.Color.SelectionForeground.SystemDefault": "false",
    }),
    "org.eclipse.jdt.ui": (RGB, JAVA_COMMON + """
pf_coloring_argument keyword
pf_coloring_assignment foreground
pf_coloring_comment singleLineComment
pf_coloring_key foreground
pf_coloring_value string
semanticHighlighting.abstractMethodInvocation.color abstractMethod
semanticHighlighting.annotation.color annotation
semanticHighlighting.class.color class
semanticHighlighting.deprecatedMember.color deprecatedMember
semanticHighlighting.enum.color enum
semanticHighlighting.field.color field
semanticHighlighting.inheritedMethodInvocation.color inheritedMethod
semanticHighlighting.interface.color interface
semanticHighlighting.localVariable.color localVariable
semanticHighlighting.localVariableDeclaration.color localVariableDeclaration
semanticHighlighting.method.color method
semanticHighlighting.methodDeclarationName.color methodDeclaration
semanticHighlighting.number.color number
semanticHighlighting.parameterVariable.color parameterVariable
semanticHighlighting.staticField.color staticField
semanticHighlighting.staticFinalField.color staticFinalField
semanticHighlighting.staticMethodInvocation.color staticMethod
semanticHighlighting.typeArgument.color typeArgument
semanticHighlighting.typeParameter.color typeParameter
sourceHoverBackgroundColor sourceHoverBackground
""", {}),
    "org.eclipse.wst.jsdt.ui": (RGB, JAVA_COMMON + """
semanticHighlighting.localVariable.color localVariable
semanticHighlighting.localVariableDeclaration.color localVariableDeclaration
semanticHighlighting.methodDeclarationName.color methodDeclaration
tagName method
""", {}),
    # editorColorKeywordPublic/Private/Protected are deliberately not mapped (PHP plugin defaults stay).
    "org.eclipse.php.ui": (WST, PHP_COMMON + "".join(
        PHP_SEM + name + "Highlighting.color " + theme + " HEX\n" for name, theme in [
            ("Class", "class"), ("Constant", "staticFinalField"), ("Deprecated", "deprecatedMember"),
            ("Field", "field"), ("Function", "method"), ("InternalClass", "class"),
            ("InternalConstant", "staticFinalField"), ("InternalFunction", "method"), ("Method", "method"),
            ("ParameterVariable", "localVariable"), ("StaticField", "staticField"),
            ("StaticMethod", "staticMethod"), ("SuperGlobal", "localVariable")]), {}),
    "com.dubture.twig.ui": (WST, PHP_COMMON + """
editorBlocknameColor keyword
editorStmtColorBoundarymaker foreground
""", {}),
    "org.eclipse.jst.jsp.ui": (WST, """
SCRIPT_AREA_BORDER bracket
commentBorder multiLineComment NI
commentText multiLineComment NI
jsp_content foreground
tagAttributeEquals foreground
tagAttributeName keyword
tagAttributeValue string
tagBorder method
""", {}),
    "org.eclipse.wst.css.ui": (WST, """
ATMARK_RULE method
ATTRIBUTE_DELIM method
ATTRIBUTE_NAME method
ATTRIBUTE_OPERATOR method
ATTRIBUTE_VALUE method
CLASS method
COLON foreground
COMBINATOR method
COMMENT multiLineComment NI
CURLY_BRACE bracket
ID method
MEDIA keyword
NORMAL foreground
PROPERTY_NAME field
PROPERTY_VALUE string
PSEUDO method
SELECTOR method
SEMI_COLON foreground
STRING string
UNIVERSAL method
URI string
""", {}),
    "org.eclipse.wst.dtd.ui": (WST, """
dtdComment multiLineComment NI
dtdData foreground
dtdDefault foreground
dtdKeyword keyword
dtdString string
dtdSymbol field
dtdTag localVariableDeclaration
dtdTagName localVariableDeclaration
""", {}),
    "org.eclipse.wst.html.ui": (WST, """
commentBorder multiLineComment NI
commentText multiLineComment NI
declBoder method
doctypeExternalId method
doctypeExternalPubref method
doctypeExtrenalSysref method
doctypeName method
entityReference keyword
tagAttributeEquals foreground
tagAttributeName field
tagAttributeValue string
tagBorder localVariable
tagName localVariableDeclaration
xmlContent foreground
""", {}),
    "org.eclipse.wst.xml.ui": (WST, """
cdataBorder method
cdataText foreground
commentBorder multiLineComment NI
commentText multiLineComment NI
declBoder method
declBorder method
doctypeExternalId method
doctypeExternalPubref method
doctypeExtrenalSysref method
doctypeName method
entityReference keyword
piBorder method
piContent foreground
tagAttributeEquals foreground
tagAttributeName field
tagAttributeValue string
tagBorder localVariable
tagName localVariableDeclaration
xmlContent foreground
""", {}),
    "org.eclipse.wst.xsl.ui": (WST, """
xsltagAttributeName field
xsltagAttributeValue string
xsltagBorder localVariable
xsltagName localVariableDeclaration
""", {}),
    # Written for this addon (not in Eclipse Color Theme).
    "org.eclipse.wst.json.ui": ("{hex} | null | false", """
COLON foreground
COMMA foreground
CURLY_BRACE foreground
OBJECT_KEY field
VALUE_BOOLEAN keyword
VALUE_NULL keyword
VALUE_NUMBER number
VALUE_STRING string JSONSTR
""", {}),
}

# How an editor stores bold / italic / strikethrough / underline:
#   wst  = inside the value (colour | background | bold | italic | strikethrough | underline)
#   java = separate keys with a suffix (java_keyword_bold)
# Keys that end in ".color" (semantic highlighting) always get separate keys (x.bold next to x.color).
STYLES = {"org.eclipse.jdt.ui": "java", "org.eclipse.wst.jsdt.ui": "java", "org.eclipse.ui.editors": ""}

OVERRIDES = {"NI": WST_NOITALIC, "HEX": HEX, "JSONSTR": "{hex} | null | false | true"}

os.makedirs(OUT, exist_ok=True)
for plugin, (template, table, constants) in MAPPINGS.items():
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<!-- Generated by tools/gen_mappings.py. template: {hex} = #RRGGBB, {rgb} = r,g,b -->',
             '<editorMapping plugin="%s" template="%s" styles="%s">' % (plugin, template, STYLES.get(plugin, "wst"))]
    for key, theme, flag in pairs(table):
        extra = ' template="%s"' % OVERRIDES[flag] if flag else ""
        lines.append('    <map key="%s" theme="%s"%s/>' % (key, theme, extra))
    for key, value in constants.items():
        lines.append('    <set key="%s" value="%s"/>' % (key, value))
    lines.append("</editorMapping>")
    with open(os.path.join(OUT, plugin + ".xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", plugin)

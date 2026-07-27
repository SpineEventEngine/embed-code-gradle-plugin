# Writing style

Apply these rules to changed Markdown and KDoc prose. Preserve a file's valid local
structure when it is more specific.

## Write for the reader

- Explain the reader's problem before the implementation or solution details.
- Use direct, active sentences and concrete verbs.
- Prefer precise project terms over synonyms, marketing language, or jargon.
- State prerequisites before steps and expected outcomes after them.
- Verify factual claims against current code, tests, build configuration, or behavior.

## Format prose consistently

- Keep changed Markdown and KDoc prose at no more than 100 characters per source line.
- Use sentence case for headings.
- Use one top-level heading and do not skip heading levels.
- Format paths, identifiers, Gradle tasks, properties, flags, commands, and literals as code.
- Put multiline commands, configuration, source, output, and other machine text in fenced
  code blocks with a language identifier when one applies.
- Align Markdown tables in source: pad every cell so column pipes line up vertically,
  and size separator cells to the padded column widths. Re-align the full table after any edit.
- Use relative links for repository files.
- Prefer reference-style links for external destinations.

## Use typography deliberately

- Reserve typographic quotation marks for actual page or section titles, such as “Requirements”.
- Use straight quotation marks for words discussed as words (use–mention), such as
  "which" and "that".
- Do not use quotation marks for emphasis, identifiers, invented labels, or technical
  terms. Use plain prose, italics, or code formatting as appropriate.
- Do not leave runts or orphans. Reflow or rewrite a paragraph, list item, or table cell
  whose final source line contains one word or an unusually short fragment.
- Avoid widows in rendered or paginated material. Keep headings with their following
  content and avoid isolating a paragraph's opening or closing line.
- Avoid rivers when a rendered layout shows repeated aligned gaps through adjacent lines.
  Reflow or rephrase the smallest affected passage.
- Do not add manual line breaks or non-breaking spaces merely to force a Markdown layout.

## Edit minimally

- Correct grammar only where needed for correctness, clarity, or consistency.
- Limit rewording and reflow to the affected sentence or paragraph.
- Preserve the author's meaning, the document's ownership, and established terminology.
- Do not restyle unrelated prose while making a focused change.
- Preserve code, machine-generated text, exact command output, logs, URLs, frontmatter,
  serialized data, and license or copyright text verbatim unless the task targets it.

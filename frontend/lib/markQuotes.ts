export interface NoteSegment {
  text: string;
  quoted: boolean;
}

// Splits noteText on each quotedPhrase's exact occurrence, in order, so the page can render
// quoted spans with <mark> inline rather than as a separate chip list (matching
// docs/design-reference/pages/reading.html). A phrase not found verbatim in the note (the
// model paraphrased it slightly) is simply not marked, not an error.
export function markQuotes(noteText: string, quotedPhrases: string[]): NoteSegment[] {
  if (quotedPhrases.length === 0) {
    return [{ text: noteText, quoted: false }];
  }

  const segments: NoteSegment[] = [{ text: noteText, quoted: false }];
  for (const phrase of quotedPhrases) {
    if (phrase === "") continue;
    for (let i = 0; i < segments.length; i++) {
      const segment = segments[i];
      if (segment.quoted) continue;
      const index = segment.text.indexOf(phrase);
      if (index === -1) continue;

      const before = segment.text.slice(0, index);
      const match = segment.text.slice(index, index + phrase.length);
      const after = segment.text.slice(index + phrase.length);
      const replacement: NoteSegment[] = [];
      if (before !== "") replacement.push({ text: before, quoted: false });
      replacement.push({ text: match, quoted: true });
      if (after !== "") replacement.push({ text: after, quoted: false });

      segments.splice(i, 1, ...replacement);
      break;
    }
  }
  return segments;
}

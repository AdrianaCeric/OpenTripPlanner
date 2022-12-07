package org.opentripplanner.openstreetmap.wayproperty;

import java.util.Map;
import java.util.regex.Pattern;
import org.opentripplanner.openstreetmap.model.OSMWithTags;
import org.opentripplanner.street.model.StreetNote;
import org.opentripplanner.street.model.StreetNoteAndMatcher;
import org.opentripplanner.street.model.StreetNoteMatcher;
import org.opentripplanner.transit.model.basic.I18NString;
import org.opentripplanner.transit.model.basic.TranslatedString;

public class NoteProperties {

  private static final Pattern patternMatcher = Pattern.compile("\\{(.*?)}");

  public String notePattern;

  public StreetNoteMatcher noteMatcher;

  public NoteProperties(String notePattern, StreetNoteMatcher noteMatcher) {
    this.notePattern = notePattern;
    this.noteMatcher = noteMatcher;
  }

  public StreetNoteAndMatcher generateNote(OSMWithTags way) {
    I18NString text;
    //TODO: this could probably be made without patternMatch for {} since all notes (at least currently) have {note} as notePattern
    if (patternMatcher.matcher(notePattern).matches()) {
      //This gets language -> translation of notePattern and all tags (which can have translations name:en for example)
      Map<String, String> noteText = way.generateI18NForPattern(notePattern);
      text = TranslatedString.getI18NString(noteText, true, false);
    } else {
      text = LocalizedStringMapper.getInstance().map(notePattern, way);
    }
    StreetNote note = new StreetNote(text);

    return new StreetNoteAndMatcher(note, noteMatcher);
  }
}

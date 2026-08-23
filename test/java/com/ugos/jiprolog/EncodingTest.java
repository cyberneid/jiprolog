/*
 * Copyright (C) 1999-2018 Ugo Chirico
 *
 * This is free software; you can redistribute it and/or
 * modify it under the terms of the Affero GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Affero GNU General Public License for more details.
 *
 * You should have received a copy of the Affero GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.ugos.jiprolog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ugos.jiprolog.engine.JIPRepresentationException;
import org.junit.jupiter.api.io.TempDir;

/**
 * Text is read and written as UTF-8, whatever the JVM's default charset is.
 *
 * <p>It used to be whatever {@code Charset.defaultCharset()} said, which is the
 * platform's locale before Java 18 and UTF-8 from Java 18 on. The same source
 * file gave three different answers depending on the JVM it ran under — correct
 * codes, the raw bytes as separate characters, or U+FFFD where the data used to
 * be. See CODE_REVIEW.md section 23.
 *
 * <p>These tests pin the *engine's* behaviour, not the JVM's: they feed bytes,
 * not strings, so a wrong charset shows up as wrong character codes rather than
 * being hidden by the test's own decoding.
 */
public class EncodingTest extends PrologTestBase
{
    @TempDir
    File tempDir;

    /**
     * Writes the source as UTF-8 bytes to a file and consults it, so the engine
     * is the one choosing the charset.
     *
     * <p>Handing it a Reader would not test anything: the test would have done
     * the decoding, and the engine's choice would never come into it. That was
     * this test's first mistake, and it made three cases pass against the very
     * build they were written to catch.
     */
    private void consultUtf8(String prologSource) throws Exception
    {
        File source = File.createTempFile("utf8", ".pl", tempDir);
        OutputStream out = new FileOutputStream(source);
        try
        {
            out.write(prologSource.getBytes(StandardCharsets.UTF_8));
        }
        finally
        {
            out.close();
        }

        engine.consultFile(source.getAbsolutePath().replace('\\', '/'));
    }

    @Nested
    @DisplayName("the engine's encoding")
    class EngineEncoding
    {
        @Test
        @DisplayName("is UTF-8, not the platform default")
        public void isUtf8()
        {
            assertEquals("UTF-8", engine.getEncoding());
            assertEquals("'UTF-8'", valueOf("current_prolog_flag(encoding, E)", "E"));
        }
    }

    @Nested
    @DisplayName("source text")
    class SourceText
    {
        @Test
        @DisplayName("keeps characters above 127")
        public void latin1Range() throws Exception
        {
            consultUtf8("caffe('caff\u00e8').\nacuta('\u00e9').\n");

            assertEquals("[99,97,102,102,232]", valueOf("caffe(A), atom_codes(A, L)", "L"));
            assertEquals("[233]", valueOf("acuta(A), atom_codes(A, L)", "L"));
        }

        @Test
        @DisplayName("keeps characters above 255")
        public void aboveLatin1() throws Exception
        {
            consultUtf8("euro('\u20ac').\ngreco('\u03b1\u03b2').\n");

            assertEquals("[8364]", valueOf("euro(A), atom_codes(A, L)", "L"));
            assertEquals("[945,946]", valueOf("greco(A), atom_codes(A, L)", "L"));
        }

        @Test
        @DisplayName("counts them as one character each")
        public void lengthIsInCharacters() throws Exception
        {
            consultUtf8("parola('caff\u00e8').\nsimbolo('\u20ac').\n");

            assertEquals("5", valueOf("parola(A), atom_length(A, N)", "N"));
            assertEquals("1", valueOf("simbolo(A), atom_length(A, N)", "N"));
        }
    }

    @Nested
    @DisplayName("a file opened with open/3")
    class FileStreams
    {
        @Test
        @DisplayName("is read as UTF-8")
        public void readsUtf8() throws Exception
        {
            File data = new File(tempDir, "data.txt");
            OutputStream out = new FileOutputStream(data);
            try
            {
                out.write("caff\u00e8 \u20ac".getBytes(StandardCharsets.UTF_8));
            }
            finally
            {
                out.close();
            }

            consult("leggi(F, Cs) :- open(F, read, S), leggi_loop(S, Cs), close(S).\n"
                  + "leggi_loop(S, [C|Cs]) :- get_code(S, C), C =\\= -1, !, leggi_loop(S, Cs).\n"
                  + "leggi_loop(_, []).\n");

            // il percorso arriva come atomo quotato, con le barre in avanti
            String path = data.getAbsolutePath().replace('\\', '/');
            assertEquals("[99,97,102,102,232,32,8364]",
                    valueOf("leggi('" + path + "', Cs)", "Cs"));
        }
    }

    @Nested
    @DisplayName("a character code")
    class CodeRange
    {
        @Test
        @DisplayName("may be any code point, not just a byte")
        public void aboveByteRange()
        {
            // the range check was 0..255, so the engine could hold a character
            // it could not construct: atom_chars accepted '€' and atom_codes
            // gave 8364 back, while atom_codes(A, [8364]) raised
            // representation_error(character_code)
            assertEquals("1", valueOf("atom_codes(A, [8364]), atom_length(A, N)", "N"));
            assertEquals("1", valueOf("atom_codes(A, [256]), atom_length(A, N)", "N"));
            assertEquals("[8364]", valueOf("char_code(C, 8364), atom_codes(C, L)", "L"));
        }

        @Test
        @DisplayName("round-trips through atom_codes and atom_chars alike")
        public void agreesWithItself()
        {
            assertEquals("[8364]", valueOf("atom_codes(A, [8364]), atom_codes(A, L)", "L"));
            assertEquals("[945,946]", valueOf("atom_codes(A, [945,946]), atom_codes(A, L)", "L"));
        }

        @Test
        @DisplayName("is still refused when it is not a code point at all")
        public void stillRejectsNonsense()
        {
            assertThrows(JIPRepresentationException.class,
                    () -> succeeds("atom_codes(_, [-1])"));
            assertThrows(JIPRepresentationException.class,
                    () -> succeeds("atom_codes(_, [1114112])"));
        }
    }

    @Nested
    @DisplayName("what the engine writes")
    class Output
    {
        @Test
        @DisplayName("comes back as the characters it was given")
        public void roundTripsThroughText() throws Exception
        {
            consultUtf8("scrivi :- atom_codes(A, [99,97,102,102,232,32,8364]), write(A).\n");

            // captureOutput decodes the engine's bytes; it used to do that as
            // ISO-8859-1 while the engine wrote UTF-8, which turned every
            // non-ASCII assertion into a silent mismatch
            assertEquals("caff\u00e8 \u20ac", captureOutput("scrivi"));
        }

        @Test
        @DisplayName("survives a full write-then-read round trip")
        public void roundTripsThroughAFile() throws Exception
        {
            File data = new File(tempDir, "round.txt");
            String path = data.getAbsolutePath().replace('\\', '/');

            consult("scrivi(F) :- open(F, write, S), atom_codes(A, [232,32,8364]), write(S, A), close(S).\n"
                  + "rileggi(F, Cs) :- open(F, read, S), leggi(S, Cs), close(S).\n"
                  + "leggi(S, [C|Cs]) :- get_code(S, C), C =\\= -1, !, leggi(S, Cs).\n"
                  + "leggi(_, []).\n");

            assertEquals("[232,32,8364]",
                    valueOf("scrivi('" + path + "'), rileggi('" + path + "', Cs)", "Cs"));
        }
    }

    @Nested
    @DisplayName("numeric escape sequences (ISO 6.4.2.1)")
    class Escapes
    {
        @Test
        @DisplayName("denote a code point, not a byte")
        public void codePointNotByte()
        {
            // \xa3\ went through (char)(byte)0xA3, which sign-extends to 0xFFA3
            assertEquals("163", valueOf("char_code('\\xa3\\', C)", "C"));
            assertEquals("233", valueOf("char_code('\\xe9\\', C)", "C"));
            assertEquals("65", valueOf("char_code('\\x41\\', C)", "C"));
        }

        @Test
        @DisplayName("take one or more hex digits, not exactly two")
        public void anyNumberOfDigits()
        {
            // \x20ac\ stopped after 20 and left "ac" in the text, which then
            // broke the closing quote
            assertEquals("8364", valueOf("char_code('\\x20ac\\', C)", "C"));
            assertEquals("[163,8364]", valueOf("atom_codes('\\xa3\\\\x20ac\\', L)", "L"));
        }

        @Test
        @DisplayName("work the same in octal")
        public void octal()
        {
            assertEquals("163", valueOf("char_code('\\243\\', C)", "C"));
            assertEquals("65", valueOf("char_code('\\101\\', C)", "C"));
        }
    }
}

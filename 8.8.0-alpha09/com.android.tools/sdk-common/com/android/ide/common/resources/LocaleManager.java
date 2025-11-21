/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Provides access to locale information such as language names and
 * language to region name mappings for the various locales.
 * <p>
 * This is derived from a number of sources:
 * <li>
 *     <li> ICU (com.ibm.icu:icu4j:71.1 or later)
 *     <li> ISO 639-2: http://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt
 *     <li> ISO 3166-2: https://www.iso.org/obp/ui/#iso:code:3166:AQ
 * </li>
 */
public class LocaleManager {
    /** Utility methods only */
    private LocaleManager() {
    }

    /**
     * Returns the name of the given region for a region code, in English.
     *
     * @param regionCode the 2 letter region code (ISO 3166-1 alpha-2), or the 3 letter region code
     *     (ISO 3166-2 alpha-3), or the 3 digit region code (UN M.49)
     * @return the name of the given region for a region code, in English, or null if not known
     */
    @Nullable
    public static String getRegionName(@NonNull String regionCode) {
        if (regionCode.length() == 2) {
            assert Character.isUpperCase(regionCode.charAt(0))
                    && Character.isUpperCase(regionCode.charAt(1)) : regionCode;
            int index = Arrays.binarySearch(ISO_3166_1_CODES, regionCode);
            if (index < 0 || index >= ISO_3166_1_TO_2.length) {
                return null;
            }
            return ISO_3166_2_NAMES[ISO_3166_1_TO_2[index]];
        } else if (regionCode.length() == 3) {
            // 3 character code is expected to be either all letters or all numbers.
            assert (Character.isUpperCase(regionCode.charAt(0))
                                    && Character.isUpperCase(regionCode.charAt(1))
                                    && Character.isUpperCase(regionCode.charAt(2)))
                            || (Character.isDigit(regionCode.charAt(0))
                                    && Character.isDigit(regionCode.charAt(1))
                                    && Character.isDigit(regionCode.charAt(2)))
                    : "regionCode " + regionCode + " unexpected";
            int index = Arrays.binarySearch(ISO_3166_2_CODES, regionCode);
            if (index < 0) {
                return null;
            }
            return ISO_3166_2_NAMES[index];
        }

        return null;
    }

    /**
     * Returns the name of the given language for a language code, in English.
     *
     * @param languageCode the 2 letter language code (ISO 639-1), or
     *                     3 letter language code (ISO 639-2)
     * @return the name of the given language for a language code, in English, or
     *         null if not known
     */
    @Nullable
    public static String getLanguageName(@NonNull String languageCode) {
        if (languageCode.length() == 2) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_1_CODES, languageCode);
            if (index < 0 || index >= ISO_639_1_TO_2.length) {
                return null;
            }
            return ISO_639_2_NAMES[ISO_639_1_TO_2[index]];
        } else if (languageCode.length() == 3) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1))
                    && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_2_CODES, languageCode);
            if (index < 0) {
                return null;
            }
            return ISO_639_2_NAMES[index];
        }

        return null;
    }

    /**
     * Returns all the known language codes
     *
     * @return all the known language codes
     */
    @NonNull
    public static List<String> getLanguageCodes() {
        return getLanguageCodes(false);
    }

    /**
     * Returns all the known language codes
     *
     * @param include3 If true, include 3-letter language codes as well (for
     *                 languages not available as 2-letter languages)
     * @return all the known language codes
     */
    @NonNull
    public static List<String> getLanguageCodes(boolean include3) {
        if (!include3) {
            return Arrays.asList(ISO_639_1_CODES);
        } else {
            List<String> codes = new ArrayList<>(ISO_639_2_CODES.length);
            for (int i = 0; i < ISO_639_2_TO_1.length; i++) {
                int iso2 = ISO_639_2_TO_1[i];
                if (iso2 != -1) {
                    codes.add(ISO_639_1_CODES[iso2]);
                } else {
                    codes.add(ISO_639_2_CODES[i]);
                }
            }
            return codes;
        }
    }

    /**
     * Returns all the known region codes
     *
     * @return all the known region codes
     */
    @NonNull
    public static List<String> getRegionCodes() {
        return getRegionCodes(false);
    }

    /**
     * Returns all the known region codes
     *
     * @param include3 If true, include 3-letter region codes as well (for
     *                 regions not available as 2-letter regions)
     * @return all the known region codes
     */
    @NonNull
    public static List<String> getRegionCodes(boolean include3) {
        if (!include3) {
            return Arrays.asList(ISO_3166_1_CODES);
        } else {
            List<String> codes = new ArrayList<>(ISO_3166_2_CODES.length);
            for (int i = 0; i < ISO_3166_2_TO_1.length; i++) {
                int iso2 = ISO_3166_2_TO_1[i];
                if (iso2 != -1) {
                    codes.add(ISO_3166_1_CODES[iso2]);
                } else {
                    codes.add(ISO_3166_2_CODES[i]);
                }
            }
            return codes;
        }
    }

    /**
     * Returns true if the given language code represents a valid/known 2 or 3 letter
     * language code. (By convention, language codes should be lower case.)
     *
     * @param languageCode the language code to look up
     * @return true if this is a known language
     */
    public static boolean isValidLanguageCode(@NonNull String languageCode) {
        if (languageCode.length() == 2) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
            return Arrays.binarySearch(ISO_639_1_CODES, languageCode) >= 0;
        } else if (languageCode.length() == 3) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1))
                    && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
            return Arrays.binarySearch(ISO_639_2_CODES, languageCode) >= 0;
        }

        return false;
    }

    /**
     * Returns true if the given region code represents a valid/known 2 or 3 letter
     * region code. (By convention, region codes should be upper case.)
     *
     * @param regionCode the region code to look up
     * @return true if this is a known region
     */
    public static boolean isValidRegionCode(@NonNull String regionCode) {
        if (regionCode.length() == 2) {
            assert Character.isUpperCase(regionCode.charAt(0))
                    && Character.isUpperCase(regionCode.charAt(1)) : regionCode;
            return Arrays.binarySearch(ISO_3166_1_CODES, regionCode) >= 0;
        } else if (regionCode.length() == 3) {
            assert Character.isUpperCase(regionCode.charAt(0))
                    && Character.isUpperCase(regionCode.charAt(1))
                    && Character.isUpperCase(regionCode.charAt(2)) : regionCode;
            return Arrays.binarySearch(ISO_3166_2_CODES, regionCode) >= 0;
        }

        return false;
    }

    /**
     * Returns the region code for the given language. <b>Note that there can be
     * many regions that speak a given language; this just picks one</b> based
     * on a set of heuristics.
     *
     * @param languageCode the language to look up
     * @return the corresponding region code, if any
     */
    @Nullable
    public static String getDefaultLanguageRegion(@NonNull String languageCode) {
        if (languageCode.length() == 2) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_1_CODES, languageCode);
            if (index < 0 || index >= ISO_639_1_TO_2.length) {
                return null;
            }
            int regionIndex = LANGUAGE_REGION[ISO_639_1_TO_2[index]];
            if (regionIndex != -1) {
                int twoLetterIndex = ISO_3166_2_TO_1[regionIndex];
                if (twoLetterIndex != -1) {
                    return ISO_3166_1_CODES[twoLetterIndex];
                } else {
                    return ISO_3166_2_CODES[regionIndex];
                }
            }
            return null;
        } else if (languageCode.length() == 3) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1))
                    && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_2_CODES, languageCode);
            if (index < 0) {
                return null;
            }
            return getRegionCode(LANGUAGE_REGION[index]);
        }

        assert false : languageCode;
        return null;
    }

    /**
     * Get the region code (either 3166-1 or if necessary, 3166-2) for the given
     * 3166-2 region code
     */
    private static String getRegionCode(int index) {
        if (index != -1) {
            int twoLetterIndex = ISO_3166_2_TO_1[index];
            if (twoLetterIndex != -1) {
                return ISO_3166_1_CODES[twoLetterIndex];
            } else {
                return ISO_3166_2_CODES[index];
            }
        }

        return null;
    }

    /** Returns the relevant regions for the given language, if known. */
    @NonNull
    public static List<String> getRelevantRegions(@NonNull String languageCode) {
        int languageIndex;
        if (languageCode.length() == 2) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_1_CODES, languageCode);
            if (index < 0 || index >= ISO_639_1_TO_2.length) {
                return Collections.emptyList();
            }
            languageIndex = ISO_639_1_TO_2[index];
        } else if (languageCode.length() == 3) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1))
                    && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
            languageIndex = Arrays.binarySearch(ISO_639_2_CODES, languageCode);
            if (languageIndex < 0) {
                return Collections.emptyList();
            }
        } else {
            assert false : languageCode;
            return Collections.emptyList();
        }

        int[] regionIndices = LANGUAGE_REGIONS[languageIndex];
        if (regionIndices == null) { // only returns non-null when there are multiple
            String regionCode = getRegionCode(LANGUAGE_REGION[languageIndex]);
            return regionCode != null ? Collections.singletonList(regionCode)
                    : Collections.emptyList();

        }

        List<String> result = new ArrayList<>(regionIndices.length);
        for (int regionIndex : regionIndices) {
            String regionCode = getRegionCode(regionIndex);
            if (regionCode != null) {
                result.add(regionCode);
            }
        }
        return result;
    }

    /**
     * Returns the corresponding ISO 639 alpha-2 code given an alpha-3 code
     *
     * @param languageCode the ISO 639 alpha-3 code
     * @return the corresponding ISO 639 alpha-2 code, if any
     */
    @Nullable
    public static String getLanguageAlpha2(@NonNull String languageCode) {
        assert languageCode.length() == 3 : languageCode;
        assert Character.isLowerCase(languageCode.charAt(0))
                && Character.isLowerCase(languageCode.charAt(1))
                && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
        int index = Arrays.binarySearch(ISO_639_2_CODES, languageCode);
        if (index < 0) {
            return null;
        }
        int alpha2 = ISO_639_2_TO_1[index];
        if (alpha2 != -1) {
            return ISO_639_1_CODES[alpha2];
        }
        return null;
    }

    /**
     * Returns the corresponding ISO 639 alpha-3 code given an alpha-2 code
     *
     * @param languageCode the ISO 639 alpha-2 code
     * @return the corresponding ISO 639 alpha-3 code, if any
     */
    @Nullable
    public static String getLanguageAlpha3(@NonNull String languageCode) {
        assert languageCode.length() == 2 : languageCode;
        assert Character.isLowerCase(languageCode.charAt(0))
                && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
        int index = Arrays.binarySearch(ISO_639_1_CODES, languageCode);
        if (index < 0) {
            return null;
        }
        int alpha2 = ISO_639_1_TO_2[index];
        if (alpha2 != -1) {
            return ISO_639_2_CODES[alpha2];
        }
        return null;
    }

    /**
     * Returns the corresponding ISO 3166 alpha-2 code given an alpha-3 code
     *
     * @param regionCode the ISO 3166 alpha-3 code
     * @return the corresponding ISO 3166 alpha-2 code, if any
     */
    @Nullable
    public static String getRegionAlpha2(@NonNull String regionCode) {
        assert regionCode.length() == 3 : regionCode;
        assert Character.isUpperCase(regionCode.charAt(0))
                && Character.isUpperCase(regionCode.charAt(1))
                && Character.isUpperCase(regionCode.charAt(2)) : regionCode;
        int index = Arrays.binarySearch(ISO_3166_2_CODES, regionCode);
        if (index < 0) {
            return null;
        }
        int alpha2 = ISO_3166_2_TO_1[index];
        if (alpha2 != -1) {
            return ISO_3166_1_CODES[alpha2];
        }
        return null;
    }

    /**
     * Returns the corresponding ISO 3166 alpha-3 code given an alpha-2 code
     *
     * @param regionCode the ISO 3166 alpha-2 code
     * @return the corresponding ISO 3166 alpha-3 code, if any
     */
    @Nullable
    public static String getRegionAlpha3(@NonNull String regionCode) {
        assert regionCode.length() == 2 : regionCode;
        assert Character.isUpperCase(regionCode.charAt(0))
                && Character.isUpperCase(regionCode.charAt(1)) : regionCode;
        int index = Arrays.binarySearch(ISO_3166_1_CODES, regionCode);
        if (index < 0) {
            return null;
        }
        int alpha2 = ISO_3166_1_TO_2[index];
        if (alpha2 != -1) {
            return ISO_3166_2_CODES[alpha2];
        }
        return null;
    }

    // The remainder of this class is generated by generate-locale-data
    // DO NOT EDIT MANUALLY

    private static final String[] ISO_639_2_CODES = new String[] {
            "aar", "abk", "ace", "ach", "ada", "ady", "afa", "afh", "afr",
            "ain", "aka", "akk", "ale", "alg", "alt", "amh", "ang", "anp",
            "apa", "ara", "arc", "arg", "arn", "arp", "art", "arw", "asm",
            "ast", "ath", "aus", "ava", "ave", "awa", "aym", "aze", "bad",
            "bai", "bak", "bal", "bam", "ban", "bas", "bat", "bej", "bel",
            "bem", "ben", "ber", "bho", "bih", "bik", "bin", "bis", "bla",
            "bnt", "bod", "bos", "bra", "bre", "btk", "bua", "bug", "bul",
            "byn", "cad", "cai", "car", "cat", "cau", "ceb", "cel", "ces",
            "cha", "chb", "che", "chg", "chk", "chm", "chn", "cho", "chp",
            "chr", "chu", "chv", "chy", "cmc", "cnr", "cop", "cor", "cos",
            "cpe", "cpf", "cpp", "cre", "crh", "crp", "csb", "cus", "cym",
            "dak", "dan", "dar", "day", "del", "den", "deu", "dgr", "din",
            "div", "doi", "dra", "dsb", "dua", "dum", "dyu", "dzo", "efi",
            "egy", "eka", "ell", "elx", "eng", "enm", "epo", "est", "eus",
            "ewe", "ewo", "fan", "fao", "fas", "fat", "fij", "fil", "fin",
            "fiu", "fon", "fra", "frm", "fro", "frr", "frs", "fry", "ful",
            "fur", "gaa", "gay", "gba", "gem", "gez", "gil", "gla", "gle",
            "glg", "glv", "gmh", "goh", "gon", "gor", "got", "grb", "grc",
            "grn", "gsw", "guj", "gwi", "hai", "hat", "hau", "haw", "heb",
            "her", "hil", "him", "hin", "hit", "hmn", "hmo", "hrv", "hsb",
            "hun", "hup", "hye", "iba", "ibo", "ido", "iii", "ijo", "iku",
            "ile", "ilo", "ina", "inc", "ind", "ine", "inh", "ipk", "ira",
            "iro", "isl", "ita", "jav", "jbo", "jpn", "jpr", "jrb", "kaa",
            "kab", "kac", "kal", "kam", "kan", "kar", "kas", "kat", "kau",
            "kaw", "kaz", "kbd", "kgp", "kha", "khi", "khm", "kho", "kik",
            "kin", "kir", "kmb", "kok", "kom", "kon", "kor", "kos", "kpe",
            "krc", "krl", "kro", "kru", "kua", "kum", "kur", "kut", "lad",
            "lah", "lam", "lao", "lat", "lav", "lez", "lim", "lin", "lit",
            "lol", "loz", "lrc", "ltz", "lua", "lub", "lug", "lui", "lun",
            "luo", "lus", "mad", "mag", "mah", "mai", "mak", "mal", "man",
            "map", "mar", "mas", "mdf", "mdr", "men", "mga", "mic", "min",
            "mis", "mkd", "mkh", "mlg", "mlt", "mnc", "mni", "mno", "moh",
            "mon", "mos", "mri", "msa", "mul", "mun", "mus", "mwl", "mwr",
            "mya", "myn", "myv", "mzn", "nah", "nai", "nap", "nau", "nav",
            "nbl", "nde", "ndo", "nds", "nep", "new", "nia", "nic", "niu",
            "nld", "nno", "nob", "nog", "non", "nor", "nqo", "nso", "nub",
            "nwc", "nya", "nym", "nyn", "nyo", "nzi", "oci", "oji", "ori",
            "orm", "osa", "oss", "ota", "oto", "paa", "pag", "pal", "pam",
            "pan", "pap", "pau", "pcm", "peo", "phi", "phn", "pli", "pol",
            "pon", "por", "pra", "pro", "pus", "que", "raj", "rap", "rar",
            "roa", "roh", "rom", "ron", "run", "rup", "rus", "sad", "sag",
            "sah", "sai", "sal", "sam", "san", "sas", "sat", "scn", "sco",
            "sel", "sem", "sga", "sgn", "shn", "sid", "sin", "sio", "sit",
            "sla", "slk", "slv", "sma", "sme", "smi", "smj", "smn", "smo",
            "sms", "sna", "snd", "snk", "sog", "som", "son", "sot", "spa",
            "sqi", "srd", "srn", "srp", "srr", "ssa", "ssw", "suk", "sun",
            "sus", "sux", "swa", "swe", "syc", "syr", "tah", "tai", "tam",
            "tat", "tel", "tem", "ter", "tet", "tgk", "tgl", "tha", "tig",
            "tir", "tiv", "tkl", "tlh", "tli", "tmh", "tog", "ton", "tpi",
            "tsi", "tsn", "tso", "tuk", "tum", "tup", "tur", "tut", "tvl",
            "twi", "tyv", "udm", "uga", "uig", "ukr", "umb", "und", "urd",
            "uzb", "vai", "ven", "vie", "vol", "vot", "wak", "wal", "war",
            "was", "wen", "wln", "wol", "xal", "xho", "yao", "yap", "yid",
            "yor", "ypk", "yrl", "zap", "zbl", "zen", "zgh", "zha", "zho",
            "znd", "zul", "zun", "zza"
    };

    @SuppressWarnings("WrongTerminology")
    private static final String[] ISO_639_2_NAMES = new String[] {
            "Afar",                                 // Code aar/aa
            "Abkhazian",                            // Code abk/ab
            "Achinese",                             // Code ace
            "Acoli",                                // Code ach
            "Adangme",                              // Code ada
            "Adyghe; Adygei",                       // Code ady
            "Afro-Asiatic languages",               // Code afa
            "Afrihili",                             // Code afh
            "Afrikaans",                            // Code afr/af
            "Ainu",                                 // Code ain
            "Akan",                                 // Code aka/ak
            "Akkadian",                             // Code akk
            "Aleut",                                // Code ale
            "Algonquian languages",                 // Code alg
            "Southern Altai",                       // Code alt
            "Amharic",                              // Code amh/am
            "English, Old (ca.450-1100)",           // Code ang
            "Angika",                               // Code anp
            "Apache languages",                     // Code apa
            "Arabic",                               // Code ara/ar
            "Official Aramaic (700-300 BCE); Imperial Aramaic (700-300 BCE)",// Code arc
            "Aragonese",                            // Code arg/an
            "Mapudungun; Mapuche",                  // Code arn
            "Arapaho",                              // Code arp
            "Artificial languages",                 // Code art
            "Arawak",                               // Code arw
            "Assamese",                             // Code asm/as
            "Asturian; Bable; Leonese; Asturleonese",// Code ast
            "Athapascan languages",                 // Code ath
            "Australian languages",                 // Code aus
            "Avaric",                               // Code ava/av
            "Avestan",                              // Code ave/ae
            "Awadhi",                               // Code awa
            "Aymara",                               // Code aym/ay
            "Azerbaijani",                          // Code aze/az
            "Banda languages",                      // Code bad
            "Bamileke languages",                   // Code bai
            "Bashkir",                              // Code bak/ba
            "Baluchi",                              // Code bal
            "Bambara",                              // Code bam/bm
            "Balinese",                             // Code ban
            "Basa",                                 // Code bas
            "Baltic languages",                     // Code bat
            "Beja; Bedawiyet",                      // Code bej
            "Belarusian",                           // Code bel/be
            "Bemba",                                // Code bem
            "Bangla",                               // Code ben/bn
            "Berber languages",                     // Code ber
            "Bhojpuri",                             // Code bho
            "Bihari languages",                     // Code bih/bh
            "Bikol",                                // Code bik
            "Bini; Edo",                            // Code bin
            "Bislama",                              // Code bis/bi
            "Siksika",                              // Code bla
            "Bantu languages",                      // Code bnt
            "Tibetan",                              // Code bod/bo
            "Bosnian",                              // Code bos/bs
            "Braj",                                 // Code bra
            "Breton",                               // Code bre/br
            "Batak languages",                      // Code btk
            "Buriat",                               // Code bua
            "Buginese",                             // Code bug
            "Bulgarian",                            // Code bul/bg
            "Blin; Bilin",                          // Code byn
            "Caddo",                                // Code cad
            "Central American Indian languages",    // Code cai
            "Galibi Carib",                         // Code car
            "Catalan",                              // Code cat/ca
            "Caucasian languages",                  // Code cau
            "Cebuano",                              // Code ceb
            "Celtic languages",                     // Code cel
            "Czech",                                // Code ces/cs
            "Chamorro",                             // Code cha/ch
            "Chibcha",                              // Code chb
            "Chechen",                              // Code che/ce
            "Chagatai",                             // Code chg
            "Chuukese",                             // Code chk
            "Mari",                                 // Code chm
            "Chinook jargon",                       // Code chn
            "Choctaw",                              // Code cho
            "Chipewyan; Dene Suline",               // Code chp
            "Cherokee",                             // Code chr
            "Church Slavic; Old Slavonic; Church Slavonic; Old Bulgarian; Old Church Slavonic",// Code chu/cu
            "Chuvash",                              // Code chv/cv
            "Cheyenne",                             // Code chy
            "Chamic languages",                     // Code cmc
            "Montenegrin",                          // Code cnr
            "Coptic",                               // Code cop
            "Cornish",                              // Code cor/kw
            "Corsican",                             // Code cos/co
            "Creoles and pidgins, English based",   // Code cpe
            "Creoles and pidgins, French-based",    // Code cpf
            "Creoles and pidgins, Portuguese-based",// Code cpp
            "Cree",                                 // Code cre/cr
            "Crimean Tatar; Crimean Turkish",       // Code crh
            "Creoles and pidgins",                  // Code crp
            "Kashubian",                            // Code csb
            "Cushitic languages",                   // Code cus
            "Welsh",                                // Code cym/cy
            "Dakota",                               // Code dak
            "Danish",                               // Code dan/da
            "Dargwa",                               // Code dar
            "Land Dayak languages",                 // Code day
            "Delaware",                             // Code del
            "Slave (Athapascan)",                   // Code den
            "German",                               // Code deu/de
            "Dogrib",                               // Code dgr
            "Dinka",                                // Code din
            "Divehi; Dhivehi; Maldivian",           // Code div/dv
            "Dogri",                                // Code doi
            "Dravidian languages",                  // Code dra
            "Lower Sorbian",                        // Code dsb
            "Duala",                                // Code dua
            "Dutch, Middle (ca.1050-1350)",         // Code dum
            "Dyula",                                // Code dyu
            "Dzongkha",                             // Code dzo/dz
            "Efik",                                 // Code efi
            "Egyptian (Ancient)",                   // Code egy
            "Ekajuk",                               // Code eka
            "Greek",                                // Code ell/el
            "Elamite",                              // Code elx
            "English",                              // Code eng/en
            "English, Middle (1100-1500)",          // Code enm
            "Esperanto",                            // Code epo/eo
            "Estonian",                             // Code est/et
            "Basque",                               // Code eus/eu
            "Ewe",                                  // Code ewe/ee
            "Ewondo",                               // Code ewo
            "Fang",                                 // Code fan
            "Faroese",                              // Code fao/fo
            "Persian",                              // Code fas/fa
            "Fanti",                                // Code fat
            "Fijian",                               // Code fij/fj
            "Filipino; Pilipino",                   // Code fil
            "Finnish",                              // Code fin/fi
            "Finno-Ugrian languages",               // Code fiu
            "Fon",                                  // Code fon
            "French",                               // Code fra/fr
            "French, Middle (ca.1400-1600)",        // Code frm
            "French, Old (842-ca.1400)",            // Code fro
            "Northern Frisian",                     // Code frr
            "Eastern Frisian",                      // Code frs
            "Western Frisian",                      // Code fry/fy
            "Fulah",                                // Code ful/ff
            "Friulian",                             // Code fur
            "Ga",                                   // Code gaa
            "Gayo",                                 // Code gay
            "Gbaya",                                // Code gba
            "Germanic languages",                   // Code gem
            "Geez",                                 // Code gez
            "Gilbertese",                           // Code gil
            "Scottish Gaelic",                      // Code gla/gd
            "Irish",                                // Code gle/ga
            "Galician",                             // Code glg/gl
            "Manx",                                 // Code glv/gv
            "German, Middle High (ca.1050-1500)",   // Code gmh
            "German, Old High (ca.750-1050)",       // Code goh
            "Gondi",                                // Code gon
            "Gorontalo",                            // Code gor
            "Gothic",                               // Code got
            "Grebo",                                // Code grb
            "Greek, Ancient (to 1453)",             // Code grc
            "Guarani",                              // Code grn/gn
            "Swiss German; Alemannic; Alsatian",    // Code gsw
            "Gujarati",                             // Code guj/gu
            "Gwich'in",                             // Code gwi
            "Haida",                                // Code hai
            "Haitian; Haitian Creole",              // Code hat/ht
            "Hausa",                                // Code hau/ha
            "Hawaiian",                             // Code haw
            "Hebrew",                               // Code heb/iw
            "Herero",                               // Code her/hz
            "Hiligaynon",                           // Code hil
            "Himachali languages; Western Pahari languages",// Code him
            "Hindi",                                // Code hin/hi
            "Hittite",                              // Code hit
            "Hmong; Mong",                          // Code hmn
            "Hiri Motu",                            // Code hmo/ho
            "Croatian",                             // Code hrv/hr
            "Upper Sorbian",                        // Code hsb
            "Hungarian",                            // Code hun/hu
            "Hupa",                                 // Code hup
            "Armenian",                             // Code hye/hy
            "Iban",                                 // Code iba
            "Igbo",                                 // Code ibo/ig
            "Ido",                                  // Code ido/io
            "Sichuan Yi",                           // Code iii/ii
            "Ijo languages",                        // Code ijo
            "Inuktitut",                            // Code iku/iu
            "Interlingue; Occidental",              // Code ile/ie
            "Iloko",                                // Code ilo
            "Interlingua",                          // Code ina/ia
            "Indic languages",                      // Code inc
            "Indonesian",                           // Code ind/in
            "Indo-European languages",              // Code ine
            "Ingush",                               // Code inh
            "Inupiaq",                              // Code ipk/ik
            "Iranian languages",                    // Code ira
            "Iroquoian languages",                  // Code iro
            "Icelandic",                            // Code isl/is
            "Italian",                              // Code ita/it
            "Javanese",                             // Code jav/jv
            "Lojban",                               // Code jbo
            "Japanese",                             // Code jpn/ja
            "Judeo-Persian",                        // Code jpr
            "Judeo-Arabic",                         // Code jrb
            "Kara-Kalpak",                          // Code kaa
            "Kabyle",                               // Code kab
            "Kachin; Jingpho",                      // Code kac
            "Kalaallisut",                          // Code kal/kl
            "Kamba",                                // Code kam
            "Kannada",                              // Code kan/kn
            "Karen languages",                      // Code kar
            "Kashmiri",                             // Code kas/ks
            "Georgian",                             // Code kat/ka
            "Kanuri",                               // Code kau/kr
            "Kawi",                                 // Code kaw
            "Kazakh",                               // Code kaz/kk
            "Kabardian",                            // Code kbd
            "Kaingang",                             // Code kgp
            "Khasi",                                // Code kha
            "Khoisan languages",                    // Code khi
            "Khmer",                                // Code khm/km
            "Khotanese; Sakan",                     // Code kho
            "Kikuyu",                               // Code kik/ki
            "Kinyarwanda",                          // Code kin/rw
            "Kyrgyz",                               // Code kir/ky
            "Kimbundu",                             // Code kmb
            "Konkani",                              // Code kok
            "Komi",                                 // Code kom/kv
            "Kongo",                                // Code kon/kg
            "Korean",                               // Code kor/ko
            "Kosraean",                             // Code kos
            "Kpelle",                               // Code kpe
            "Karachay-Balkar",                      // Code krc
            "Karelian",                             // Code krl
            "Kru languages",                        // Code kro
            "Kurukh",                               // Code kru
            "Kuanyama; Kwanyama",                   // Code kua/kj
            "Kumyk",                                // Code kum
            "Kurdish",                              // Code kur/ku
            "Kutenai",                              // Code kut
            "Ladino",                               // Code lad
            "Lahnda",                               // Code lah
            "Lamba",                                // Code lam
            "Lao",                                  // Code lao/lo
            "Latin",                                // Code lat/la
            "Latvian",                              // Code lav/lv
            "Lezghian",                             // Code lez
            "Limburgan; Limburger; Limburgish",     // Code lim/li
            "Lingala",                              // Code lin/ln
            "Lithuanian",                           // Code lit/lt
            "Mongo",                                // Code lol
            "Lozi",                                 // Code loz
            "Northern Luri",                        // Code lrc
            "Luxembourgish",                        // Code ltz/lb
            "Luba-Lulua",                           // Code lua
            "Luba-Katanga",                         // Code lub/lu
            "Ganda",                                // Code lug/lg
            "Luiseno",                              // Code lui
            "Lunda",                                // Code lun
            "Luo (Kenya and Tanzania)",             // Code luo
            "Lushai",                               // Code lus
            "Madurese",                             // Code mad
            "Magahi",                               // Code mag
            "Marshallese",                          // Code mah/mh
            "Maithili",                             // Code mai
            "Makasar",                              // Code mak
            "Malayalam",                            // Code mal/ml
            "Mandingo",                             // Code man
            "Austronesian languages",               // Code map
            "Marathi",                              // Code mar/mr
            "Masai",                                // Code mas
            "Moksha",                               // Code mdf
            "Mandar",                               // Code mdr
            "Mende",                                // Code men
            "Irish, Middle (900-1200)",             // Code mga
            "Mi'kmaq; Micmac",                      // Code mic
            "Minangkabau",                          // Code min
            "Uncoded languages",                    // Code mis
            "Macedonian",                           // Code mkd/mk
            "Mon-Khmer languages",                  // Code mkh
            "Malagasy",                             // Code mlg/mg
            "Maltese",                              // Code mlt/mt
            "Manchu",                               // Code mnc
            "Manipuri",                             // Code mni
            "Manobo languages",                     // Code mno
            "Mohawk",                               // Code moh
            "Mongolian",                            // Code mon/mn
            "Mossi",                                // Code mos
            "Māori",                                // Code mri/mi
            "Malay",                                // Code msa/ms
            "Multiple languages",                   // Code mul
            "Munda languages",                      // Code mun
            "Creek",                                // Code mus
            "Mirandese",                            // Code mwl
            "Marwari",                              // Code mwr
            "Burmese",                              // Code mya/my
            "Mayan languages",                      // Code myn
            "Erzya",                                // Code myv
            "Mazanderani",                          // Code mzn
            "Nahuatl languages",                    // Code nah
            "North American Indian languages",      // Code nai
            "Neapolitan",                           // Code nap
            "Nauru",                                // Code nau/na
            "Navajo; Navaho",                       // Code nav/nv
            "Ndebele, South; South Ndebele",        // Code nbl/nr
            "North Ndebele",                        // Code nde/nd
            "Ndonga",                               // Code ndo/ng
            "Low German; Low Saxon; German, Low; Saxon, Low",// Code nds
            "Nepali",                               // Code nep/ne
            "Nepal Bhasa; Newari",                  // Code new
            "Nias",                                 // Code nia
            "Niger-Kordofanian languages",          // Code nic
            "Niuean",                               // Code niu
            "Dutch",                                // Code nld/nl
            "Norwegian Nynorsk",                    // Code nno/nn
            "Norwegian Bokmål",                     // Code nob/nb
            "Nogai",                                // Code nog
            "Norse, Old",                           // Code non
            "Norwegian",                            // Code nor/no
            "N'Ko",                                 // Code nqo
            "Pedi; Sepedi; Northern Sotho",         // Code nso
            "Nubian languages",                     // Code nub
            "Classical Newari; Old Newari; Classical Nepal Bhasa",// Code nwc
            "Chichewa; Chewa; Nyanja",              // Code nya/ny
            "Nyamwezi",                             // Code nym
            "Nyankole",                             // Code nyn
            "Nyoro",                                // Code nyo
            "Nzima",                                // Code nzi
            "Occitan (post 1500)",                  // Code oci/oc
            "Ojibwa",                               // Code oji/oj
            "Odia",                                 // Code ori/or
            "Oromo",                                // Code orm/om
            "Osage",                                // Code osa
            "Ossetic",                              // Code oss/os
            "Turkish, Ottoman (1500-1928)",         // Code ota
            "Otomian languages",                    // Code oto
            "Papuan languages",                     // Code paa
            "Pangasinan",                           // Code pag
            "Pahlavi",                              // Code pal
            "Pampanga; Kapampangan",                // Code pam
            "Punjabi",                              // Code pan/pa
            "Papiamento",                           // Code pap
            "Palauan",                              // Code pau
            "Nigerian Pidgin",                      // Code pcm
            "Persian, Old (ca.600-400 B.C.)",       // Code peo
            "Philippine languages",                 // Code phi
            "Phoenician",                           // Code phn
            "Pali",                                 // Code pli/pi
            "Polish",                               // Code pol/pl
            "Pohnpeian",                            // Code pon
            "Portuguese",                           // Code por/pt
            "Prakrit languages",                    // Code pra
            "Provençal, Old (to 1500); Occitan, Old (to 1500)",// Code pro
            "Pashto",                               // Code pus/ps
            "Quechua",                              // Code que/qu
            "Rajasthani",                           // Code raj
            "Rapanui",                              // Code rap
            "Rarotongan; Cook Islands Maori",       // Code rar
            "Romance languages",                    // Code roa
            "Romansh",                              // Code roh/rm
            "Romany",                               // Code rom
            "Romanian",                             // Code ron/ro
            "Rundi",                                // Code run/rn
            "Aromanian; Arumanian; Macedo-Romanian",// Code rup
            "Russian",                              // Code rus/ru
            "Sandawe",                              // Code sad
            "Sango",                                // Code sag/sg
            "Yakut",                                // Code sah
            "South American Indian languages",      // Code sai
            "Salishan languages",                   // Code sal
            "Samaritan Aramaic",                    // Code sam
            "Sanskrit",                             // Code san/sa
            "Sasak",                                // Code sas
            "Santali",                              // Code sat
            "Sicilian",                             // Code scn
            "Scots",                                // Code sco
            "Selkup",                               // Code sel
            "Semitic languages",                    // Code sem
            "Irish, Old (to 900)",                  // Code sga
            "Sign Languages",                       // Code sgn
            "Shan",                                 // Code shn
            "Sidamo",                               // Code sid
            "Sinhala",                              // Code sin/si
            "Siouan languages",                     // Code sio
            "Sino-Tibetan languages",               // Code sit
            "Slavic languages",                     // Code sla
            "Slovak",                               // Code slk/sk
            "Slovenian",                            // Code slv/sl
            "Southern Sami",                        // Code sma
            "Northern Sami",                        // Code sme/se
            "Sami languages",                       // Code smi
            "Lule Sami",                            // Code smj
            "Inari Sami",                           // Code smn
            "Samoan",                               // Code smo/sm
            "Skolt Sami",                           // Code sms
            "Shona",                                // Code sna/sn
            "Sindhi",                               // Code snd/sd
            "Soninke",                              // Code snk
            "Sogdian",                              // Code sog
            "Somali",                               // Code som/so
            "Songhai languages",                    // Code son
            "Sotho, Southern",                      // Code sot/st
            "Spanish",                              // Code spa/es
            "Albanian",                             // Code sqi/sq
            "Sardinian",                            // Code srd/sc
            "Sranan Tongo",                         // Code srn
            "Serbian",                              // Code srp/sr
            "Serer",                                // Code srr
            "Nilo-Saharan languages",               // Code ssa
            "Swati",                                // Code ssw/ss
            "Sukuma",                               // Code suk
            "Sundanese",                            // Code sun/su
            "Susu",                                 // Code sus
            "Sumerian",                             // Code sux
            "Swahili",                              // Code swa/sw
            "Swedish",                              // Code swe/sv
            "Classical Syriac",                     // Code syc
            "Syriac",                               // Code syr
            "Tahitian",                             // Code tah/ty
            "Tai languages",                        // Code tai
            "Tamil",                                // Code tam/ta
            "Tatar",                                // Code tat/tt
            "Telugu",                               // Code tel/te
            "Timne",                                // Code tem
            "Tereno",                               // Code ter
            "Tetum",                                // Code tet
            "Tajik",                                // Code tgk/tg
            "Tagalog",                              // Code tgl/tl
            "Thai",                                 // Code tha/th
            "Tigre",                                // Code tig
            "Tigrinya",                             // Code tir/ti
            "Tiv",                                  // Code tiv
            "Tokelau",                              // Code tkl
            "Klingon; tlhIngan-Hol",                // Code tlh
            "Tlingit",                              // Code tli
            "Tamashek",                             // Code tmh
            "Tonga (Nyasa)",                        // Code tog
            "Tongan",                               // Code ton/to
            "Tok Pisin",                            // Code tpi
            "Tsimshian",                            // Code tsi
            "Tswana",                               // Code tsn/tn
            "Tsonga",                               // Code tso/ts
            "Turkmen",                              // Code tuk/tk
            "Tumbuka",                              // Code tum
            "Tupi languages",                       // Code tup
            "Turkish",                              // Code tur/tr
            "Altaic languages",                     // Code tut
            "Tuvalu",                               // Code tvl
            "Twi",                                  // Code twi/tw
            "Tuvinian",                             // Code tyv
            "Udmurt",                               // Code udm
            "Ugaritic",                             // Code uga
            "Uyghur",                               // Code uig/ug
            "Ukrainian",                            // Code ukr/uk
            "Umbundu",                              // Code umb
            "Undetermined",                         // Code und
            "Urdu",                                 // Code urd/ur
            "Uzbek",                                // Code uzb/uz
            "Vai",                                  // Code vai
            "Venda",                                // Code ven/ve
            "Vietnamese",                           // Code vie/vi
            "Volapük",                              // Code vol/vo
            "Votic",                                // Code vot
            "Wakashan languages",                   // Code wak
            "Wolaitta; Wolaytta",                   // Code wal
            "Waray",                                // Code war
            "Washo",                                // Code was
            "Sorbian languages",                    // Code wen
            "Walloon",                              // Code wln/wa
            "Wolof",                                // Code wol/wo
            "Kalmyk; Oirat",                        // Code xal
            "Xhosa",                                // Code xho/xh
            "Yao",                                  // Code yao
            "Yapese",                               // Code yap
            "Yiddish",                              // Code yid/ji
            "Yoruba",                               // Code yor/yo
            "Yupik languages",                      // Code ypk
            "Nheengatu",                            // Code yrl
            "Zapotec",                              // Code zap
            "Blissymbols; Blissymbolics; Bliss",    // Code zbl
            "Zenaga",                               // Code zen
            "Standard Moroccan Tamazight",          // Code zgh
            "Zhuang; Chuang",                       // Code zha/za
            "Chinese",                              // Code zho/zh
            "Zande languages",                      // Code znd
            "Zulu",                                 // Code zul/zu
            "Zuni",                                 // Code zun
            "Zaza; Dimili; Dimli; Kirdki; Kirmanjki; Zazaki"// Code zza
    };

    private static final String[] ISO_639_1_CODES = new String[] {
            "aa", "ab", "ae", "af", "ak", "am", "an", "ar", "as", "av", "ay",
            "az", "ba", "be", "bg", "bh", "bi", "bm", "bn", "bo", "br", "bs",
            "ca", "ce", "ch", "co", "cr", "cs", "cu", "cv", "cy", "da", "de",
            "dv", "dz", "ee", "el", "en", "eo", "es", "et", "eu", "fa", "ff",
            "fi", "fj", "fo", "fr", "fy", "ga", "gd", "gl", "gn", "gu", "gv",
            "ha", "he", "hi", "ho", "hr", "ht", "hu", "hy", "hz", "ia", "id",
            "ie", "ig", "ii", "ik", "in", "io", "is", "it", "iu", "iw", "ja",
            "ji", "jv", "ka", "kg", "ki", "kj", "kk", "kl", "km", "kn", "ko",
            "kr", "ks", "ku", "kv", "kw", "ky", "la", "lb", "lg", "li", "ln",
            "lo", "lt", "lu", "lv", "mg", "mh", "mi", "mk", "ml", "mn", "mr",
            "ms", "mt", "my", "na", "nb", "nd", "ne", "ng", "nl", "nn", "no",
            "nr", "nv", "ny", "oc", "oj", "om", "or", "os", "pa", "pi", "pl",
            "ps", "pt", "qu", "rm", "rn", "ro", "ru", "rw", "sa", "sc", "sd",
            "se", "sg", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr", "ss",
            "st", "su", "sv", "sw", "ta", "te", "tg", "th", "ti", "tk", "tl",
            "tn", "to", "tr", "ts", "tt", "tw", "ty", "ug", "uk", "ur", "uz",
            "ve", "vi", "vo", "wa", "wo", "xh", "yi", "yo", "za", "zh", "zu"

    };

    // Each element corresponds to an ISO 639-1 code, and contains the index
    // for the corresponding ISO 639-2 code
    private static final int[] ISO_639_1_TO_2 = new int[] {
              0,   1,  31,   8,  10,  15,  21,  19,  26,  30,  33,
             34,  37,  44,  62,  49,  52,  39,  46,  55,  58,  56,
             67,  74,  72,  89,  93,  71,  82,  83,  98, 100, 105,
            108, 115, 126, 119, 121, 123, 404, 124, 125, 130, 143,
            134, 132, 129, 137, 142, 152, 151, 153, 162, 164, 154,
            168, 170, 174, 177, 178, 167, 180, 182, 171, 191, 193,
            189, 184, 186, 196, 193, 185, 199, 200, 188, 170, 203,
            476, 201, 214, 230, 224, 238, 217, 209, 222, 211, 231,
            215, 213, 240, 229,  88, 226, 246, 255, 258, 249, 250,
            245, 251, 257, 247, 282, 265, 290, 280, 268, 288, 271,
            291, 283, 297, 304, 317, 307, 310, 308, 315, 316, 320,
            306, 305, 325, 330, 331, 333, 332, 335, 342, 349, 350,
            355, 352, 356, 361, 364, 363, 366, 225, 373, 406, 398,
            391, 368, 384, 388, 389, 395, 397, 401, 405, 408, 411,
            403, 413, 417, 416, 422, 424, 428, 430, 432, 444, 429,
            442, 439, 447, 443, 423, 450, 420, 454, 455, 458, 459,
            461, 462, 463, 470, 471, 473, 476, 477, 484, 485, 487

    };

    // Each element corresponds to an ISO 639-2 code, and contains the index
    // for the corresponding ISO 639-1 code, or -1 if not represented
    private static final int[] ISO_639_2_TO_1 = new int[] {
              0,   1,  -1,  -1,  -1,  -1,  -1,  -1,   3,  -1,   4,
             -1,  -1,  -1,  -1,   5,  -1,  -1,  -1,   7,  -1,   6,
             -1,  -1,  -1,  -1,   8,  -1,  -1,  -1,   9,   2,  -1,
             10,  11,  -1,  -1,  12,  -1,  17,  -1,  -1,  -1,  -1,
             13,  -1,  18,  -1,  -1,  15,  -1,  -1,  16,  -1,  -1,
             19,  21,  -1,  20,  -1,  -1,  -1,  14,  -1,  -1,  -1,
             -1,  22,  -1,  -1,  -1,  27,  24,  -1,  23,  -1,  -1,
             -1,  -1,  -1,  -1,  -1,  28,  29,  -1,  -1,  -1,  -1,
             92,  25,  -1,  -1,  -1,  26,  -1,  -1,  -1,  -1,  30,
             -1,  31,  -1,  -1,  -1,  -1,  32,  -1,  -1,  33,  -1,
             -1,  -1,  -1,  -1,  -1,  34,  -1,  -1,  -1,  36,  -1,
             37,  -1,  38,  40,  41,  35,  -1,  -1,  46,  42,  -1,
             45,  -1,  44,  -1,  -1,  47,  -1,  -1,  -1,  -1,  48,
             43,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  50,  49,  51,
             54,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  52,  -1,  53,
             -1,  -1,  60,  55,  -1,  75,  63,  -1,  -1,  57,  -1,
             -1,  58,  59,  -1,  61,  -1,  62,  -1,  67,  71,  68,
             -1,  74,  66,  -1,  64,  -1,  70,  -1,  -1,  69,  -1,
             -1,  72,  73,  78,  -1,  76,  -1,  -1,  -1,  -1,  -1,
             84,  -1,  86,  -1,  89,  79,  88,  -1,  83,  -1,  -1,
             -1,  -1,  85,  -1,  81, 139,  93,  -1,  -1,  91,  80,
             87,  -1,  -1,  -1,  -1,  -1,  -1,  82,  -1,  90,  -1,
             -1,  -1,  -1,  99,  94, 102,  -1,  97,  98, 100,  -1,
             -1,  -1,  95,  -1, 101,  96,  -1,  -1,  -1,  -1,  -1,
             -1, 104,  -1,  -1, 107,  -1,  -1, 109,  -1,  -1,  -1,
             -1,  -1,  -1,  -1,  -1, 106,  -1, 103, 111,  -1,  -1,
             -1,  -1, 108,  -1, 105, 110,  -1,  -1,  -1,  -1,  -1,
            112,  -1,  -1,  -1,  -1,  -1,  -1, 113, 122, 121, 115,
            117,  -1, 116,  -1,  -1,  -1,  -1, 118, 119, 114,  -1,
             -1, 120,  -1,  -1,  -1,  -1, 123,  -1,  -1,  -1,  -1,
            124, 125, 127, 126,  -1, 128,  -1,  -1,  -1,  -1,  -1,
             -1, 129,  -1,  -1,  -1,  -1,  -1,  -1, 130, 131,  -1,
            133,  -1,  -1, 132, 134,  -1,  -1,  -1,  -1, 135,  -1,
            137, 136,  -1, 138,  -1, 144,  -1,  -1,  -1,  -1, 140,
             -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1, 145,
             -1,  -1,  -1, 146, 147,  -1, 143,  -1,  -1,  -1, 148,
             -1, 149, 142,  -1,  -1, 150,  -1, 154,  39, 151, 141,
             -1, 152,  -1,  -1, 153,  -1, 155,  -1,  -1, 157, 156,
             -1,  -1, 171,  -1, 158, 169, 159,  -1,  -1,  -1, 160,
            164, 161,  -1, 162,  -1,  -1,  -1,  -1,  -1,  -1, 166,
             -1,  -1, 165, 168, 163,  -1,  -1, 167,  -1,  -1, 170,
             -1,  -1,  -1, 172, 173,  -1,  -1, 174, 175,  -1, 176,
            177, 178,  -1,  -1,  -1,  -1,  -1,  -1, 179, 180,  -1,
            181,  -1,  -1,  77, 183,  -1,  -1,  -1,  -1,  -1,  -1,
            184, 185,  -1, 186,  -1,  -1
    };
    private static final String[] ISO_3166_2_CODES =
            new String[] {
                "001", "002", "004", "005", "008", "009", "010", "011", "012",
                "013", "014", "015", "016", "017", "018", "019", "020", "021",
                "024", "028", "029", "030", "031", "032", "034", "035", "036",
                "039", "040", "044", "048", "050", "051", "052", "053", "054",
                "056", "057", "060", "061", "064", "068", "070", "072", "074",
                "076", "084", "086", "090", "092", "096", "100", "104", "108",
                "112", "116", "120", "124", "132", "136", "140", "142", "143",
                "144", "145", "148", "150", "151", "152", "154", "155", "156",
                "162", "166", "170", "174", "175", "178", "180", "184", "188",
                "191", "192", "196", "202", "203", "204", "208", "212", "214",
                "218", "222", "226", "231", "232", "233", "234", "238", "239",
                "242", "246", "248", "250", "254", "258", "260", "262", "266",
                "268", "270", "275", "276", "288", "292", "296", "300", "304",
                "308", "312", "316", "320", "324", "328", "332", "334", "336",
                "340", "344", "348", "352", "356", "360", "364", "368", "372",
                "376", "380", "384", "388", "392", "398", "400", "404", "408",
                "410", "414", "417", "418", "419", "422", "426", "428", "430",
                "434", "438", "440", "442", "446", "450", "454", "458", "462",
                "466", "470", "474", "478", "480", "484", "492", "496", "498",
                "499", "500", "504", "508", "512", "516", "520", "524", "528",
                "531", "533", "534", "535", "540", "548", "554", "558", "562",
                "566", "570", "574", "578", "580", "581", "583", "584", "585",
                "586", "591", "598", "600", "604", "608", "612", "616", "620",
                "624", "626", "630", "634", "638", "642", "643", "646", "652",
                "654", "659", "660", "662", "663", "666", "670", "674", "678",
                "680", "682", "686", "688", "690", "694", "702", "703", "704",
                "705", "706", "710", "716", "724", "728", "729", "732", "740",
                "744", "748", "752", "756", "760", "762", "764", "768", "772",
                "776", "780", "784", "788", "792", "795", "796", "798", "800",
                "804", "807", "818", "826", "830", "831", "832", "833", "834",
                "840", "850", "854", "858", "860", "862", "876", "882", "887",
                "894", "ABW", "AFG", "AGO", "AIA", "ALA", "ALB", "AND", "ARE",
                "ARG", "ARM", "ASM", "ATA", "ATF", "ATG", "AUS", "AUT", "AZE",
                "BDI", "BEL", "BEN", "BES", "BFA", "BGD", "BGR", "BHR", "BHS",
                "BIH", "BLM", "BLR", "BLZ", "BMU", "BOL", "BRA", "BRB", "BRN",
                "BTN", "BVT", "BWA", "CAF", "CAN", "CCK", "CHE", "CHL", "CHN",
                "CIV", "CMR", "COD", "COG", "COK", "COL", "COM", "CPV", "CRI",
                "CUB", "CUW", "CXR", "CYM", "CYP", "CZE", "DEU", "DGA", "DJI",
                "DMA", "DNK", "DOM", "DZA", "ECU", "EGY", "ERI", "ESH", "ESP",
                "EST", "ETH", "FIN", "FJI", "FLK", "FRA", "FRO", "FSM", "GAB",
                "GBR", "GEO", "GGY", "GHA", "GIB", "GIN", "GLP", "GMB", "GNB",
                "GNQ", "GRC", "GRD", "GRL", "GTM", "GUF", "GUM", "GUY", "HKG",
                "HMD", "HND", "HRV", "HTI", "HUN", "IDN", "IMN", "IND", "IOT",
                "IRL", "IRN", "IRQ", "ISL", "ISR", "ITA", "JAM", "JEY", "JOR",
                "JPN", "KAZ", "KEN", "KGZ", "KHM", "KIR", "KNA", "KOR", "KWT",
                "LAO", "LBN", "LBR", "LBY", "LCA", "LIE", "LKA", "LSO", "LTU",
                "LUX", "LVA", "MAC", "MAF", "MAR", "MCO", "MDA", "MDG", "MDV",
                "MEX", "MHL", "MKD", "MLI", "MLT", "MMR", "MNE", "MNG", "MNP",
                "MOZ", "MRT", "MSR", "MTQ", "MUS", "MWI", "MYS", "MYT", "NAM",
                "NCL", "NER", "NFK", "NGA", "NIC", "NIU", "NLD", "NOR", "NPL",
                "NRU", "NZL", "OMN", "PAK", "PAN", "PCN", "PER", "PHL", "PLW",
                "PNG", "POL", "PRI", "PRK", "PRT", "PRY", "PSE", "PYF", "QAT",
                "REU", "ROU", "RUS", "RWA", "SAU", "SDN", "SEN", "SGP", "SGS",
                "SHN", "SJM", "SLB", "SLE", "SLV", "SMR", "SOM", "SPM", "SRB",
                "SSD", "STP", "SUR", "SVK", "SVN", "SWE", "SWZ", "SXM", "SYC",
                "SYR", "TCA", "TCD", "TGO", "THA", "TJK", "TKL", "TKM", "TLS",
                "TON", "TTO", "TUN", "TUR", "TUV", "TWN", "TZA", "UGA", "UKR",
                "UMI", "URY", "USA", "UZB", "VAT", "VCT", "VEN", "VGB", "VIR",
                "VNM", "VUT", "WLF", "WSM", "XEA", "XIC", "XKK", "YEM", "ZAF",
                "ZMB", "ZWE"
            };

    private static final String[] ISO_3166_2_NAMES =
            new String[] {
                "World", // Code 001
                "Africa", // Code 002
                "Afghanistan", // Code 004
                "South America", // Code 005
                "Albania", // Code 008
                "Oceania", // Code 009
                "Antarctica", // Code 010
                "Western Africa", // Code 011
                "Algeria", // Code 012
                "Central America", // Code 013
                "Eastern Africa", // Code 014
                "Northern Africa", // Code 015
                "American Samoa", // Code 016
                "Middle Africa", // Code 017
                "Southern Africa", // Code 018
                "Americas", // Code 019
                "Andorra", // Code 020
                "Northern America", // Code 021
                "Angola", // Code 024
                "Antigua and Barbuda", // Code 028
                "Caribbean", // Code 029
                "Eastern Asia", // Code 030
                "Azerbaijan", // Code 031
                "Argentina", // Code 032
                "Southern Asia", // Code 034
                "South-eastern Asia", // Code 035
                "Australia", // Code 036
                "Southern Europe", // Code 039
                "Austria", // Code 040
                "Bahamas", // Code 044
                "Bahrain", // Code 048
                "Bangladesh", // Code 050
                "Armenia", // Code 051
                "Barbados", // Code 052
                "Australia and New Zealand", // Code 053
                "Melanesia", // Code 054
                "Belgium", // Code 056
                "Micronesia", // Code 057
                "Bermuda", // Code 060
                "Polynesia", // Code 061
                "Bhutan", // Code 064
                "Bolivia (Plurinational State of)", // Code 068
                "Bosnia and Herzegovina", // Code 070
                "Botswana", // Code 072
                "Bouvet Island", // Code 074
                "Brazil", // Code 076
                "Belize", // Code 084
                "British Indian Ocean Territory", // Code 086
                "Solomon Islands", // Code 090
                "British Virgin Islands", // Code 092
                "Brunei Darussalam", // Code 096
                "Bulgaria", // Code 100
                "Myanmar", // Code 104
                "Burundi", // Code 108
                "Belarus", // Code 112
                "Cambodia", // Code 116
                "Cameroon", // Code 120
                "Canada", // Code 124
                "Cabo Verde", // Code 132
                "Cayman Islands", // Code 136
                "Central African Republic", // Code 140
                "Asia", // Code 142
                "Central Asia", // Code 143
                "Sri Lanka", // Code 144
                "Western Asia", // Code 145
                "Chad", // Code 148
                "Europe", // Code 150
                "Eastern Europe", // Code 151
                "Chile", // Code 152
                "Northern Europe", // Code 154
                "Western Europe", // Code 155
                "China", // Code 156
                "Christmas Island", // Code 162
                "Cocos (Keeling) Islands", // Code 166
                "Colombia", // Code 170
                "Comoros", // Code 174
                "Mayotte", // Code 175
                "Congo", // Code 178
                "Democratic Republic of the Congo", // Code 180
                "Cook Islands", // Code 184
                "Costa Rica", // Code 188
                "Croatia", // Code 191
                "Cuba", // Code 192
                "Cyprus", // Code 196
                "Sub-Saharan Africa", // Code 202
                "Czechia", // Code 203
                "Benin", // Code 204
                "Denmark", // Code 208
                "Dominica", // Code 212
                "Dominican Republic", // Code 214
                "Ecuador", // Code 218
                "El Salvador", // Code 222
                "Equatorial Guinea", // Code 226
                "Ethiopia", // Code 231
                "Eritrea", // Code 232
                "Estonia", // Code 233
                "Faroe Islands", // Code 234
                "Falkland Islands (Malvinas)", // Code 238
                "South Georgia and the South Sandwich Islands", // Code 239
                "Fiji", // Code 242
                "Finland", // Code 246
                "Åland Islands", // Code 248
                "France", // Code 250
                "French Guiana", // Code 254
                "French Polynesia", // Code 258
                "French Southern Territories", // Code 260
                "Djibouti", // Code 262
                "Gabon", // Code 266
                "Georgia", // Code 268
                "Gambia", // Code 270
                "State of Palestine", // Code 275
                "Germany", // Code 276
                "Ghana", // Code 288
                "Gibraltar", // Code 292
                "Kiribati", // Code 296
                "Greece", // Code 300
                "Greenland", // Code 304
                "Grenada", // Code 308
                "Guadeloupe", // Code 312
                "Guam", // Code 316
                "Guatemala", // Code 320
                "Guinea", // Code 324
                "Guyana", // Code 328
                "Haiti", // Code 332
                "Heard Island and McDonald Islands", // Code 334
                "Holy See", // Code 336
                "Honduras", // Code 340
                "China, Hong Kong Special Administrative Region", // Code 344
                "Hungary", // Code 348
                "Iceland", // Code 352
                "India", // Code 356
                "Indonesia", // Code 360
                "Iran (Islamic Republic of)", // Code 364
                "Iraq", // Code 368
                "Ireland", // Code 372
                "Israel", // Code 376
                "Italy", // Code 380
                "Côte d’Ivoire", // Code 384
                "Jamaica", // Code 388
                "Japan", // Code 392
                "Kazakhstan", // Code 398
                "Jordan", // Code 400
                "Kenya", // Code 404
                "Democratic People's Republic of Korea", // Code 408
                "Republic of Korea", // Code 410
                "Kuwait", // Code 414
                "Kyrgyzstan", // Code 417
                "Lao People's Democratic Republic", // Code 418
                "Latin America and the Caribbean", // Code 419
                "Lebanon", // Code 422
                "Lesotho", // Code 426
                "Latvia", // Code 428
                "Liberia", // Code 430
                "Libya", // Code 434
                "Liechtenstein", // Code 438
                "Lithuania", // Code 440
                "Luxembourg", // Code 442
                "China, Macao Special Administrative Region", // Code 446
                "Madagascar", // Code 450
                "Malawi", // Code 454
                "Malaysia", // Code 458
                "Maldives", // Code 462
                "Mali", // Code 466
                "Malta", // Code 470
                "Martinique", // Code 474
                "Mauritania", // Code 478
                "Mauritius", // Code 480
                "Mexico", // Code 484
                "Monaco", // Code 492
                "Mongolia", // Code 496
                "Republic of Moldova", // Code 498
                "Montenegro", // Code 499
                "Montserrat", // Code 500
                "Morocco", // Code 504
                "Mozambique", // Code 508
                "Oman", // Code 512
                "Namibia", // Code 516
                "Nauru", // Code 520
                "Nepal", // Code 524
                "Netherlands", // Code 528
                "Curaçao", // Code 531
                "Aruba", // Code 533
                "Sint Maarten (Dutch part)", // Code 534
                "Bonaire, Sint Eustatius and Saba", // Code 535
                "New Caledonia", // Code 540
                "Vanuatu", // Code 548
                "New Zealand", // Code 554
                "Nicaragua", // Code 558
                "Niger", // Code 562
                "Nigeria", // Code 566
                "Niue", // Code 570
                "Norfolk Island", // Code 574
                "Norway", // Code 578
                "Northern Mariana Islands", // Code 580
                "United States Minor Outlying Islands", // Code 581
                "Micronesia (Federated States of)", // Code 583
                "Marshall Islands", // Code 584
                "Palau", // Code 585
                "Pakistan", // Code 586
                "Panama", // Code 591
                "Papua New Guinea", // Code 598
                "Paraguay", // Code 600
                "Peru", // Code 604
                "Philippines", // Code 608
                "Pitcairn", // Code 612
                "Poland", // Code 616
                "Portugal", // Code 620
                "Guinea-Bissau", // Code 624
                "Timor-Leste", // Code 626
                "Puerto Rico", // Code 630
                "Qatar", // Code 634
                "Réunion", // Code 638
                "Romania", // Code 642
                "Russian Federation", // Code 643
                "Rwanda", // Code 646
                "Saint Barthélemy", // Code 652
                "Saint Helena", // Code 654
                "Saint Kitts and Nevis", // Code 659
                "Anguilla", // Code 660
                "Saint Lucia", // Code 662
                "Saint Martin (French Part)", // Code 663
                "Saint Pierre and Miquelon", // Code 666
                "Saint Vincent and the Grenadines", // Code 670
                "San Marino", // Code 674
                "Sao Tome and Principe", // Code 678
                "Sark", // Code 680
                "Saudi Arabia", // Code 682
                "Senegal", // Code 686
                "Serbia", // Code 688
                "Seychelles", // Code 690
                "Sierra Leone", // Code 694
                "Singapore", // Code 702
                "Slovakia", // Code 703
                "Viet Nam", // Code 704
                "Slovenia", // Code 705
                "Somalia", // Code 706
                "South Africa", // Code 710
                "Zimbabwe", // Code 716
                "Spain", // Code 724
                "South Sudan", // Code 728
                "Sudan", // Code 729
                "Western Sahara", // Code 732
                "Suriname", // Code 740
                "Svalbard and Jan Mayen Islands", // Code 744
                "Eswatini", // Code 748
                "Sweden", // Code 752
                "Switzerland", // Code 756
                "Syrian Arab Republic", // Code 760
                "Tajikistan", // Code 762
                "Thailand", // Code 764
                "Togo", // Code 768
                "Tokelau", // Code 772
                "Tonga", // Code 776
                "Trinidad and Tobago", // Code 780
                "United Arab Emirates", // Code 784
                "Tunisia", // Code 788
                "Türkiye", // Code 792
                "Turkmenistan", // Code 795
                "Turks and Caicos Islands", // Code 796
                "Tuvalu", // Code 798
                "Uganda", // Code 800
                "Ukraine", // Code 804
                "North Macedonia", // Code 807
                "Egypt", // Code 818
                "United Kingdom of Great Britain and Northern Ireland", // Code 826
                "Channel Islands", // Code 830
                "Guernsey", // Code 831
                "Jersey", // Code 832
                "Isle of Man", // Code 833
                "United Republic of Tanzania", // Code 834
                "United States of America", // Code 840
                "United States Virgin Islands", // Code 850
                "Burkina Faso", // Code 854
                "Uruguay", // Code 858
                "Uzbekistan", // Code 860
                "Venezuela (Bolivarian Republic of)", // Code 862
                "Wallis and Futuna Islands", // Code 876
                "Samoa", // Code 882
                "Yemen", // Code 887
                "Zambia", // Code 894
                "Aruba", // Code ABW/AW
                "Afghanistan", // Code AFG/AF
                "Angola", // Code AGO/AO
                "Anguilla", // Code AIA/AI
                "Åland Islands", // Code ALA/AX
                "Albania", // Code ALB/AL
                "Andorra", // Code AND/AD
                "United Arab Emirates", // Code ARE/AE
                "Argentina", // Code ARG/AR
                "Armenia", // Code ARM/AM
                "American Samoa", // Code ASM/AS
                "Antarctica", // Code ATA/AQ
                "French Southern Territories (the)", // Code ATF/TF
                "Antigua & Barbuda", // Code ATG/AG
                "Australia", // Code AUS/AU
                "Austria", // Code AUT/AT
                "Azerbaijan", // Code AZE/AZ
                "Burundi", // Code BDI/BI
                "Belgium", // Code BEL/BE
                "Benin", // Code BEN/BJ
                "Caribbean Netherlands", // Code BES/BQ
                "Burkina Faso", // Code BFA/BF
                "Bangladesh", // Code BGD/BD
                "Bulgaria", // Code BGR/BG
                "Bahrain", // Code BHR/BH
                "Bahamas", // Code BHS/BS
                "Bosnia & Herzegovina", // Code BIH/BA
                "St. Barthélemy", // Code BLM/BL
                "Belarus", // Code BLR/BY
                "Belize", // Code BLZ/BZ
                "Bermuda", // Code BMU/BM
                "Bolivia", // Code BOL/BO
                "Brazil", // Code BRA/BR
                "Barbados", // Code BRB/BB
                "Brunei", // Code BRN/BN
                "Bhutan", // Code BTN/BT
                "Bouvet Island", // Code BVT/BV
                "Botswana", // Code BWA/BW
                "Central African Republic", // Code CAF/CF
                "Canada", // Code CAN/CA
                "Cocos (Keeling) Islands", // Code CCK/CC
                "Switzerland", // Code CHE/CH
                "Chile", // Code CHL/CL
                "China", // Code CHN/CN
                "Côte d’Ivoire", // Code CIV/CI
                "Cameroon", // Code CMR/CM
                "Congo - Kinshasa", // Code COD/CD
                "Congo - Brazzaville", // Code COG/CG
                "Cook Islands", // Code COK/CK
                "Colombia", // Code COL/CO
                "Comoros", // Code COM/KM
                "Cape Verde", // Code CPV/CV
                "Costa Rica", // Code CRI/CR
                "Cuba", // Code CUB/CU
                "Curaçao", // Code CUW/CW
                "Christmas Island", // Code CXR/CX
                "Cayman Islands", // Code CYM/KY
                "Cyprus", // Code CYP/CY
                "Czechia", // Code CZE/CZ
                "Germany", // Code DEU/DE
                "Diego Garcia", // Code DGA/DG
                "Djibouti", // Code DJI/DJ
                "Dominica", // Code DMA/DM
                "Denmark", // Code DNK/DK
                "Dominican Republic", // Code DOM/DO
                "Algeria", // Code DZA/DZ
                "Ecuador", // Code ECU/EC
                "Egypt", // Code EGY/EG
                "Eritrea", // Code ERI/ER
                "Western Sahara", // Code ESH/EH
                "Spain", // Code ESP/ES
                "Estonia", // Code EST/EE
                "Ethiopia", // Code ETH/ET
                "Finland", // Code FIN/FI
                "Fiji", // Code FJI/FJ
                "Falkland Islands", // Code FLK/FK
                "France", // Code FRA/FR
                "Faroe Islands", // Code FRO/FO
                "Micronesia", // Code FSM/FM
                "Gabon", // Code GAB/GA
                "United Kingdom", // Code GBR/GB
                "Georgia", // Code GEO/GE
                "Guernsey", // Code GGY/GG
                "Ghana", // Code GHA/GH
                "Gibraltar", // Code GIB/GI
                "Guinea", // Code GIN/GN
                "Guadeloupe", // Code GLP/GP
                "Gambia", // Code GMB/GM
                "Guinea-Bissau", // Code GNB/GW
                "Equatorial Guinea", // Code GNQ/GQ
                "Greece", // Code GRC/GR
                "Grenada", // Code GRD/GD
                "Greenland", // Code GRL/GL
                "Guatemala", // Code GTM/GT
                "French Guiana", // Code GUF/GF
                "Guam", // Code GUM/GU
                "Guyana", // Code GUY/GY
                "Hong Kong SAR China", // Code HKG/HK
                "Heard Island and McDonald Islands", // Code HMD/HM
                "Honduras", // Code HND/HN
                "Croatia", // Code HRV/HR
                "Haiti", // Code HTI/HT
                "Hungary", // Code HUN/HU
                "Indonesia", // Code IDN/ID
                "Isle of Man", // Code IMN/IM
                "India", // Code IND/IN
                "British Indian Ocean Territory", // Code IOT/IO
                "Ireland", // Code IRL/IE
                "Iran", // Code IRN/IR
                "Iraq", // Code IRQ/IQ
                "Iceland", // Code ISL/IS
                "Israel", // Code ISR/IL
                "Italy", // Code ITA/IT
                "Jamaica", // Code JAM/JM
                "Jersey", // Code JEY/JE
                "Jordan", // Code JOR/JO
                "Japan", // Code JPN/JP
                "Kazakhstan", // Code KAZ/KZ
                "Kenya", // Code KEN/KE
                "Kyrgyzstan", // Code KGZ/KG
                "Cambodia", // Code KHM/KH
                "Kiribati", // Code KIR/KI
                "St. Kitts & Nevis", // Code KNA/KN
                "South Korea", // Code KOR/KR
                "Kuwait", // Code KWT/KW
                "Laos", // Code LAO/LA
                "Lebanon", // Code LBN/LB
                "Liberia", // Code LBR/LR
                "Libya", // Code LBY/LY
                "St. Lucia", // Code LCA/LC
                "Liechtenstein", // Code LIE/LI
                "Sri Lanka", // Code LKA/LK
                "Lesotho", // Code LSO/LS
                "Lithuania", // Code LTU/LT
                "Luxembourg", // Code LUX/LU
                "Latvia", // Code LVA/LV
                "Macao SAR China", // Code MAC/MO
                "St. Martin", // Code MAF/MF
                "Morocco", // Code MAR/MA
                "Monaco", // Code MCO/MC
                "Moldova", // Code MDA/MD
                "Madagascar", // Code MDG/MG
                "Maldives", // Code MDV/MV
                "Mexico", // Code MEX/MX
                "Marshall Islands", // Code MHL/MH
                "North Macedonia", // Code MKD/MK
                "Mali", // Code MLI/ML
                "Malta", // Code MLT/MT
                "Myanmar (Burma)", // Code MMR/MM
                "Montenegro", // Code MNE/ME
                "Mongolia", // Code MNG/MN
                "Northern Mariana Islands", // Code MNP/MP
                "Mozambique", // Code MOZ/MZ
                "Mauritania", // Code MRT/MR
                "Montserrat", // Code MSR/MS
                "Martinique", // Code MTQ/MQ
                "Mauritius", // Code MUS/MU
                "Malawi", // Code MWI/MW
                "Malaysia", // Code MYS/MY
                "Mayotte", // Code MYT/YT
                "Namibia", // Code NAM/NA
                "New Caledonia", // Code NCL/NC
                "Niger", // Code NER/NE
                "Norfolk Island", // Code NFK/NF
                "Nigeria", // Code NGA/NG
                "Nicaragua", // Code NIC/NI
                "Niue", // Code NIU/NU
                "Netherlands", // Code NLD/NL
                "Norway", // Code NOR/NO
                "Nepal", // Code NPL/NP
                "Nauru", // Code NRU/NR
                "New Zealand", // Code NZL/NZ
                "Oman", // Code OMN/OM
                "Pakistan", // Code PAK/PK
                "Panama", // Code PAN/PA
                "Pitcairn Islands", // Code PCN/PN
                "Peru", // Code PER/PE
                "Philippines", // Code PHL/PH
                "Palau", // Code PLW/PW
                "Papua New Guinea", // Code PNG/PG
                "Poland", // Code POL/PL
                "Puerto Rico", // Code PRI/PR
                "North Korea", // Code PRK/KP
                "Portugal", // Code PRT/PT
                "Paraguay", // Code PRY/PY
                "Palestinian Territories", // Code PSE/PS
                "French Polynesia", // Code PYF/PF
                "Qatar", // Code QAT/QA
                "Réunion", // Code REU/RE
                "Romania", // Code ROU/RO
                "Russia", // Code RUS/RU
                "Rwanda", // Code RWA/RW
                "Saudi Arabia", // Code SAU/SA
                "Sudan", // Code SDN/SD
                "Senegal", // Code SEN/SN
                "Singapore", // Code SGP/SG
                "South Georgia and the South Sandwich Islands", // Code SGS/GS
                "St. Helena", // Code SHN/SH
                "Svalbard & Jan Mayen", // Code SJM/SJ
                "Solomon Islands", // Code SLB/SB
                "Sierra Leone", // Code SLE/SL
                "El Salvador", // Code SLV/SV
                "San Marino", // Code SMR/SM
                "Somalia", // Code SOM/SO
                "St. Pierre & Miquelon", // Code SPM/PM
                "Serbia", // Code SRB/RS
                "South Sudan", // Code SSD/SS
                "São Tomé & Príncipe", // Code STP/ST
                "Suriname", // Code SUR/SR
                "Slovakia", // Code SVK/SK
                "Slovenia", // Code SVN/SI
                "Sweden", // Code SWE/SE
                "Eswatini", // Code SWZ/SZ
                "Sint Maarten", // Code SXM/SX
                "Seychelles", // Code SYC/SC
                "Syria", // Code SYR/SY
                "Turks & Caicos Islands", // Code TCA/TC
                "Chad", // Code TCD/TD
                "Togo", // Code TGO/TG
                "Thailand", // Code THA/TH
                "Tajikistan", // Code TJK/TJ
                "Tokelau", // Code TKL/TK
                "Turkmenistan", // Code TKM/TM
                "Timor-Leste", // Code TLS/TL
                "Tonga", // Code TON/TO
                "Trinidad & Tobago", // Code TTO/TT
                "Tunisia", // Code TUN/TN
                "Turkey", // Code TUR/TR
                "Tuvalu", // Code TUV/TV
                "Taiwan", // Code TWN/TW
                "Tanzania", // Code TZA/TZ
                "Uganda", // Code UGA/UG
                "Ukraine", // Code UKR/UA
                "U.S. Outlying Islands", // Code UMI/UM
                "Uruguay", // Code URY/UY
                "United States", // Code USA/US
                "Uzbekistan", // Code UZB/UZ
                "Vatican City", // Code VAT/VA
                "St. Vincent & Grenadines", // Code VCT/VC
                "Venezuela", // Code VEN/VE
                "British Virgin Islands", // Code VGB/VG
                "U.S. Virgin Islands", // Code VIR/VI
                "Vietnam", // Code VNM/VN
                "Vanuatu", // Code VUT/VU
                "Wallis & Futuna", // Code WLF/WF
                "Samoa", // Code WSM/WS
                "Ceuta & Melilla", // Code XEA/EA
                "Canary Islands", // Code XIC/IC
                "Kosovo", // Code XKK/XK
                "Yemen", // Code YEM/YE
                "South Africa", // Code ZAF/ZA
                "Zambia", // Code ZMB/ZM
                "Zimbabwe" // Code ZWE/ZW
            };

    private static final String[] ISO_3166_1_CODES = new String[] {
            "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS",
            "AT", "AU", "AW", "AX", "AZ", "BA", "BB", "BD", "BE", "BF", "BG",
            "BH", "BI", "BJ", "BL", "BM", "BN", "BO", "BQ", "BR", "BS", "BT",
            "BV", "BW", "BY", "BZ", "CA", "CC", "CD", "CF", "CG", "CH", "CI",
            "CK", "CL", "CM", "CN", "CO", "CR", "CU", "CV", "CW", "CX", "CY",
            "CZ", "DE", "DG", "DJ", "DK", "DM", "DO", "DZ", "EA", "EC", "EE",
            "EG", "EH", "ER", "ES", "ET", "FI", "FJ", "FK", "FM", "FO", "FR",
            "GA", "GB", "GD", "GE", "GF", "GG", "GH", "GI", "GL", "GM", "GN",
            "GP", "GQ", "GR", "GS", "GT", "GU", "GW", "GY", "HK", "HM", "HN",
            "HR", "HT", "HU", "IC", "ID", "IE", "IL", "IM", "IN", "IO", "IQ",
            "IR", "IS", "IT", "JE", "JM", "JO", "JP", "KE", "KG", "KH", "KI",
            "KM", "KN", "KP", "KR", "KW", "KY", "KZ", "LA", "LB", "LC", "LI",
            "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME",
            "MF", "MG", "MH", "MK", "ML", "MM", "MN", "MO", "MP", "MQ", "MR",
            "MS", "MT", "MU", "MV", "MW", "MX", "MY", "MZ", "NA", "NC", "NE",
            "NF", "NG", "NI", "NL", "NO", "NP", "NR", "NU", "NZ", "OM", "PA",
            "PE", "PF", "PG", "PH", "PK", "PL", "PM", "PN", "PR", "PS", "PT",
            "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW", "SA", "SB", "SC",
            "SD", "SE", "SG", "SH", "SI", "SJ", "SK", "SL", "SM", "SN", "SO",
            "SR", "SS", "ST", "SV", "SX", "SY", "SZ", "TC", "TD", "TF", "TG",
            "TH", "TJ", "TK", "TL", "TM", "TN", "TO", "TR", "TT", "TV", "TW",
            "TZ", "UA", "UG", "UM", "US", "UY", "UZ", "VA", "VC", "VE", "VG",
            "VI", "VN", "VU", "WF", "WS", "XK", "YE", "YT", "ZA", "ZM", "ZW"

    };

    // Each element corresponds to an ISO2 code, and contains the index
    // for the corresponding ISO3 code
    private static final int[] ISO_3166_1_TO_2 =
            new int[] {
                286, 287, 281, 293, 283, 285, 289, 282, 291, 288, 290,
                295, 294, 280, 284, 296, 306, 313, 302, 298, 301, 303,
                304, 297, 299, 307, 310, 314, 311, 300, 312, 305, 315,
                316, 317, 308, 309, 319, 320, 326, 318, 327, 321, 324,
                328, 322, 325, 323, 329, 332, 333, 331, 334, 335, 337,
                338, 339, 340, 341, 343, 342, 344, 345, 526, 346, 351,
                347, 349, 348, 350, 352, 353, 354, 355, 358, 357, 356,
                359, 360, 371, 361, 374, 362, 363, 364, 372, 367, 365,
                366, 369, 370, 476, 373, 375, 368, 376, 377, 378, 379,
                380, 381, 382, 527, 383, 387, 391, 384, 385, 386, 389,
                388, 390, 392, 394, 393, 395, 396, 398, 399, 400, 401,
                330, 402, 462, 403, 404, 336, 397, 405, 406, 409, 410,
                411, 407, 412, 413, 414, 415, 408, 418, 419, 420, 429,
                417, 421, 424, 425, 426, 428, 430, 416, 431, 435, 433,
                434, 427, 436, 422, 437, 423, 438, 432, 440, 441, 442,
                443, 444, 445, 447, 448, 449, 450, 446, 451, 452, 454,
                456, 466, 459, 457, 453, 460, 484, 455, 461, 465, 463,
                458, 464, 467, 468, 469, 485, 470, 471, 472, 479, 494,
                473, 491, 475, 477, 490, 478, 489, 480, 482, 474, 483,
                488, 486, 487, 481, 493, 495, 492, 496, 497, 292, 498,
                499, 500, 501, 503, 502, 506, 504, 507, 505, 508, 509,
                510, 512, 511, 513, 515, 514, 516, 517, 518, 519, 520,
                521, 522, 523, 524, 525, 528, 529, 439, 530, 531, 532
            };

    // Each element corresponds to an ISO3 code, and contains the index
    // for the corresponding ISO2 code, or -1 if not represented
    private static final int[] ISO_3166_2_TO_1 =
            new int[] {
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, 13, 2, 7, 4, 14, 5, 0, 1, 9, 6, 10, 8, 218, 3, 12, 11,
                15, 23, 19, 24, 29, 20, 18, 21, 22, 31, 16, 25, 35, 36, 26, 28, 30, 17, 27, 32, 33,
                34, 40, 37, 38, 42, 45, 47, 43, 46, 39, 41, 44, 48, 121, 51, 49, 50, 52, 53, 126,
                54, 55, 56, 57, 58, 60, 59, 61, 62, 64, 66, 68, 67, 69, 65, 70, 71, 72, 73, 76, 75,
                74, 77, 78, 80, 82, 83, 84, 87, 88, 86, 94, 89, 90, 79, 85, 92, 81, 93, 95, 96, 97,
                98, 99, 100, 101, 103, 106, 107, 108, 104, 110, 109, 111, 105, 112, 114, 113, 115,
                116, 127, 117, 118, 119, 120, 122, 124, 125, 128, 129, 133, 138, 130, 131, 132, 134,
                135, 136, 137, 150, 143, 139, 140, 141, 144, 157, 159, 145, 146, 147, 155, 148, 142,
                149, 151, 161, 153, 154, 152, 156, 158, 160, 249, 162, 163, 164, 165, 166, 167, 172,
                168, 169, 170, 171, 173, 174, 180, 175, 183, 176, 179, 187, 178, 181, 184, 123, 186,
                188, 185, 177, 189, 190, 191, 193, 194, 195, 198, 207, 200, 91, 201, 203, 196, 205,
                212, 206, 208, 182, 192, 210, 211, 209, 204, 202, 199, 215, 213, 197, 214, 216, 217,
                219, 220, 221, 222, 224, 223, 226, 228, 225, 227, 229, 230, 231, 233, 232, 234, 236,
                235, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 63, 102, 247, 248, 250, 251,
                252
            };
    // Language afr: NAM,ZAF
    private static final int[] REGIONS_AFR = new int[] {440, 530};
    // Language ara:
    // ARE,BHR,COM,DJI,DZA,EGY,ERI,ESH,IRQ,ISR,JOR,KWT,LBN,LBY,MAR,MRT,OMN,PSE,QAT,SAU,SDN,SOM,SSD,SYR,TCD,TUN,YEM
    private static final int[] REGIONS_ARA =
            new int[] {
                287, 304, 330, 341, 345, 347, 348, 349, 389, 391, 395, 404, 406, 408, 418, 433, 452,
                465, 467, 472, 473, 483, 486, 495, 497, 506, 529
            };
    // Language ben: BGD,IND
    private static final int[] REGIONS_BEN = new int[] {302, 385};
    // Language bod: CHN,IND
    private static final int[] REGIONS_BOD = new int[] {323, 385};
    // Language cat: AND,ESP,FRA,ITA
    private static final int[] REGIONS_CAT = new int[] {286, 350, 356, 392};
    // Language dan: DNK,GRL
    private static final int[] REGIONS_DAN = new int[] {343, 372};
    // Language deu: DEU,AUT,BEL,CHE,ITA,LIE,LUX
    private static final int[] REGIONS_DEU = new int[] {339, 295, 298, 321, 392, 410, 414};
    // Language ell: CYP,GRC
    private static final int[] REGIONS_ELL = new int[] {337, 370};
    // Language eng:
    // AIA,ARE,ASM,ATG,AUS,AUT,BDI,BEL,BHS,BLZ,BMU,BRB,BWA,CAN,CCK,CHE,CMR,COK,CXR,CYM,CYP,DEU,DGA,DMA,DNK,ERI,FIN,FJI,FLK,FSM,GBR,GGY,GHA,GIB,GMB,GRD,GUM,GUY,HKG,IMN,IND,IOT,IRL,ISR,JAM,JEY,KEN,KIR,KNA,LBR,LCA,LSO,MAC,MDG,MDV,MHL,MLT,MNP,MSR,MUS,MWI,MYS,NAM,NFK,NGA,NIU,NLD,NRU,NZL,PAK,PCN,PHL,PLW,PNG,PRI,RWA,SDN,SGP,SHN,SLB,SLE,SSD,SVN,SWE,SWZ,SXM,SYC,TCA,TKL,TON,TTO,TUV,TZA,UGA,UMI,USA,VCT,VGB,VIR,VUT,WSM,ZAF,ZMB,ZWE
    private static final int[] REGIONS_ENG =
            new int[] {
                283, 287, 290, 293, 294, 295, 297, 298, 305, 309, 310, 313, 317, 319, 320, 321, 325,
                328, 335, 336, 337, 339, 340, 342, 343, 348, 353, 354, 355, 358, 360, 362, 363, 364,
                367, 371, 375, 376, 377, 384, 385, 386, 387, 391, 393, 394, 398, 401, 402, 407, 409,
                412, 416, 421, 422, 424, 427, 431, 434, 436, 437, 438, 440, 443, 444, 446, 447, 450,
                451, 453, 455, 457, 458, 459, 461, 471, 473, 475, 477, 479, 480, 486, 490, 491, 492,
                493, 494, 496, 501, 504, 505, 508, 510, 511, 513, 515, 518, 520, 521, 523, 525, 530,
                531, 532
            };
    // Language ewe: GHA,TGO
    private static final int[] REGIONS_EWE = new int[] {363, 498};
    // Language fao: DNK,FRO
    private static final int[] REGIONS_FAO = new int[] {343, 357};
    // Language fas: AFG,IRN
    private static final int[] REGIONS_FAS = new int[] {281, 388};
    // Language fra:
    // FRA,BDI,BEL,BEN,BFA,BLM,CAF,CAN,CHE,CIV,CMR,COD,COG,COM,DJI,DZA,GAB,GIN,GLP,GNQ,GUF,HTI,LUX,MAF,MAR,MCO,MDG,MLI,MRT,MTQ,MUS,MYT,NCL,NER,PYF,REU,RWA,SEN,SPM,SYC,SYR,TCD,TGO,TUN,VUT,WLF
    private static final int[] REGIONS_FRA =
            new int[] {
                356, 297, 298, 299, 301, 307, 318, 319, 321, 324, 325, 326, 327, 330, 341, 345, 359,
                365, 366, 369, 374, 381, 414, 417, 418, 419, 421, 426, 433, 435, 436, 439, 441, 442,
                466, 468, 471, 474, 484, 494, 495, 497, 498, 506, 523, 524
            };
    // Language ful: BFA,CMR,GHA,GIN,GMB,GNB,LBR,MRT,NER,NGA,SEN,SLE
    private static final int[] REGIONS_FUL =
            new int[] {301, 325, 363, 365, 367, 368, 407, 433, 442, 444, 474, 480};
    // Language gle: GBR,IRL
    private static final int[] REGIONS_GLE = new int[] {360, 387};
    // Language hau: GHA,NER,NGA
    private static final int[] REGIONS_HAU = new int[] {363, 442, 444};
    // Language hrv: HRV,BIH
    private static final int[] REGIONS_HRV = new int[] {380, 306};
    // Language ita: ITA,CHE,SMR,VAT
    private static final int[] REGIONS_ITA = new int[] {392, 321, 482, 517};
    // Language kor: KOR,PRK
    private static final int[] REGIONS_KOR = new int[] {403, 462};
    // Language lin: AGO,CAF,COD,COG
    private static final int[] REGIONS_LIN = new int[] {282, 318, 326, 327};
    // Language lrc: IRN,IRQ
    private static final int[] REGIONS_LRC = new int[] {388, 389};
    // Language msa: BRN,IDN,MYS,SGP
    private static final int[] REGIONS_MSA = new int[] {314, 383, 438, 475};
    // Language nep: IND,NPL
    private static final int[] REGIONS_NEP = new int[] {385, 449};
    // Language nld: NLD,ABW,BEL,BES,CUW,SUR,SXM
    private static final int[] REGIONS_NLD = new int[] {447, 280, 298, 300, 334, 488, 493};
    // Language nob: NOR,SJM
    private static final int[] REGIONS_NOB = new int[] {448, 478};
    // Language orm: ETH,KEN
    private static final int[] REGIONS_ORM = new int[] {352, 398};
    // Language oss: GEO,RUS
    private static final int[] REGIONS_OSS = new int[] {361, 470};
    // Language pan: IND,PAK
    private static final int[] REGIONS_PAN = new int[] {385, 453};
    // Language por: AGO,BRA,CHE,CPV,GNB,GNQ,LUX,MAC,MOZ,PRT,STP,TLS
    private static final int[] REGIONS_POR =
            new int[] {282, 312, 321, 331, 368, 369, 414, 416, 432, 463, 487, 503};
    // Language pus: AFG,PAK
    private static final int[] REGIONS_PUS = new int[] {281, 453};
    // Language que: BOL,ECU,PER
    private static final int[] REGIONS_QUE = new int[] {311, 346, 456};
    // Language ron: MDA,ROU
    private static final int[] REGIONS_RON = new int[] {420, 469};
    // Language rus: RUS,BLR,KAZ,KGZ,MDA,UKR
    private static final int[] REGIONS_RUS = new int[] {470, 308, 397, 399, 420, 512};
    // Language sme: FIN,NOR,SWE
    private static final int[] REGIONS_SME = new int[] {353, 448, 491};
    // Language snd: IND,PAK
    private static final int[] REGIONS_SND = new int[] {385, 453};
    // Language som: SOM,DJI,ETH,KEN
    private static final int[] REGIONS_SOM = new int[] {483, 341, 352, 398};
    // Language spa:
    // ARG,BLZ,BOL,BRA,CHL,COL,CRI,CUB,DOM,ECU,ESP,GNQ,GTM,HND,MEX,NIC,PAN,PER,PHL,PRI,PRY,SLV,URY,USA,VEN,XEA,XIC
    private static final int[] REGIONS_SPA =
            new int[] {
                288, 309, 311, 312, 322, 329, 332, 333, 344, 346, 350, 369, 373, 379, 423, 445, 454,
                456, 457, 461, 464, 481, 514, 515, 519, 526, 527
            };
    // Language sqi: ALB,MKD,XKK
    private static final int[] REGIONS_SQI = new int[] {285, 425, 528};
    // Language srp: BIH,MNE,SRB,XKK
    private static final int[] REGIONS_SRP = new int[] {306, 429, 485, 528};
    // Language swa: COD,KEN,TZA,UGA
    private static final int[] REGIONS_SWA = new int[] {326, 398, 510, 511};
    // Language swe: SWE,ALA,FIN
    private static final int[] REGIONS_SWE = new int[] {491, 284, 353};
    // Language tam: IND,LKA,MYS,SGP
    private static final int[] REGIONS_TAM = new int[] {385, 411, 438, 475};
    // Language tir: ERI,ETH
    private static final int[] REGIONS_TIR = new int[] {348, 352};
    // Language tur: TUR,CYP
    private static final int[] REGIONS_TUR = new int[] {507, 337};
    // Language urd: IND,PAK
    private static final int[] REGIONS_URD = new int[] {385, 453};
    // Language uzb: UZB,AFG
    private static final int[] REGIONS_UZB = new int[] {516, 281};
    // Language yor: BEN,NGA
    private static final int[] REGIONS_YOR = new int[] {299, 444};
    // Language yrl: BRA,COL,VEN
    private static final int[] REGIONS_YRL = new int[] {312, 329, 519};
    // Language zho: CHN,HKG,MAC,SGP,TWN
    private static final int[] REGIONS_ZHO = new int[] {323, 377, 416, 475, 509};

    private static final int[][] LANGUAGE_REGIONS = new int[][] {
            null, null, null, null, null, null, null, null, REGIONS_AFR,
            null, null, null, null, null, null, null, null, null,
            null, REGIONS_ARA, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_BEN, null, null, null,
            null, null, null, null, null, REGIONS_BOD, null,
            null, null, null, null, null, null, null, null, null,
            null, REGIONS_CAT, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            REGIONS_DAN, null, null, null, null, REGIONS_DEU,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, REGIONS_ELL, null, REGIONS_ENG,
            null, null, null, null, REGIONS_EWE, null, null,
            REGIONS_FAO, REGIONS_FAS, null, null, null, null,
            null, null, REGIONS_FRA, null, null, null, null,
            null, REGIONS_FUL, null, null, null, null, null,
            null, null, null, REGIONS_GLE, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_HAU, null, null, null,
            null, null, null, null, null, null, REGIONS_HRV,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_ITA, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            REGIONS_KOR, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_LIN, null, null, null,
            REGIONS_LRC, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_MSA, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, REGIONS_NEP,
            null, null, null, null, REGIONS_NLD, null, REGIONS_NOB,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, REGIONS_ORM,
            null, REGIONS_OSS, null, null, null, null, null,
            null, REGIONS_PAN, null, null, null, null, null,
            null, null, null, null, REGIONS_POR, null, null,
            REGIONS_PUS, REGIONS_QUE, null, null, null, null,
            null, null, REGIONS_RON, null, null, REGIONS_RUS,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, REGIONS_SME,
            null, null, null, null, null, null, REGIONS_SND,
            null, null, REGIONS_SOM, null, null, REGIONS_SPA,
            REGIONS_SQI, null, null, REGIONS_SRP, null, null,
            null, null, null, null, null, REGIONS_SWA, REGIONS_SWE,
            null, null, null, null, REGIONS_TAM, null, null,
            null, null, null, null, null, null, null, REGIONS_TIR,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, REGIONS_TUR, null,
            null, null, null, null, null, null, null, null, null,
            REGIONS_URD, REGIONS_UZB, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, REGIONS_YOR, null, REGIONS_YRL,
            null, null, null, null, null, REGIONS_ZHO, null,
            null, null, null
    };

    private static final int[] LANGUAGE_REGION =
            new int[] {
                -1, -1, -1, -1, -1, -1, -1, -1, 440, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 287,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 302, -1, -1, -1, -1, -1, -1, -1, -1, 323, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, 286, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 343, -1, -1,
                -1, -1, 339, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 337, -1, 283, -1,
                -1, -1, -1, 363, -1, -1, 343, 281, -1, -1, -1, -1, -1, -1, 356, -1, -1, -1, -1, -1,
                301, -1, -1, -1, -1, -1, -1, -1, -1, 360, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 363, -1, -1, -1, -1, -1, -1, -1, -1, -1, 380, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 392, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, 403, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 282, -1, -1, -1, 388, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 314, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 385, -1, -1, -1, -1, 447, -1, 448, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 352, -1, 361, -1, -1, -1, -1, -1, -1, 385, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 282, -1, -1, 281, 311, -1, -1, -1, -1, -1, -1, 420, -1, -1, 470, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 353, -1, -1, -1, -1, -1, -1, 385, -1, -1, 483, -1, -1, 288, 285, -1, -1,
                306, -1, -1, -1, -1, -1, -1, -1, 326, 491, -1, -1, -1, -1, 385, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 348, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 507,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 385, 516, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 299, -1, 312, -1, -1, -1, -1, -1, 323, -1, -1,
                -1, -1
            };

    static {
        // These maps should have been generated programmatically; look for accidental edits
        //noinspection ConstantConditions
        assert ISO_639_2_CODES.length == 490;
        //noinspection ConstantConditions
        assert ISO_639_2_NAMES.length == 490;
        //noinspection ConstantConditions
        assert ISO_639_2_TO_1.length == 490;
        //noinspection ConstantConditions
        assert ISO_639_1_CODES.length == 187;
        //noinspection ConstantConditions
        assert ISO_639_1_TO_2.length == 187;

        //noinspection ConstantConditions
        assert ISO_3166_2_CODES.length == 533;
        //noinspection ConstantConditions
        assert ISO_3166_2_NAMES.length == 533;
        //noinspection ConstantConditions
        assert ISO_3166_2_TO_1.length == 533;
        //noinspection ConstantConditions
        assert ISO_3166_1_CODES.length == 253;
        //noinspection ConstantConditions
        assert ISO_3166_1_TO_2.length == 253;

        //noinspection ConstantConditions
        assert LANGUAGE_REGION.length == 490;
        //noinspection ConstantConditions
        assert LANGUAGE_REGIONS.length == 490;
    }
}

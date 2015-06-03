package com.soreepeong.darknova.core;

import android.content.Context;
import android.content.res.Resources;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.Random;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Soreepeong on 2015-04-27.
 * String related functions.
 */
public class StringTools {

	/** Relative time template array */
	public static String ARRAY_RELATIVE_TIME_STRINGS[]; // = new String[]{"1 second ago", "% seconds ago", "1 minute ago", "% minutes ago", "1 hour ago", "% hours ago", "1 day ago", "% days ago", "1 month ago", "% months ago", "1 year ago", "% months ago", "1 century ago", "% centuries ago", "1 millennium ago", "% millennia ago"};
	public static String ARRAY_RELATIVE_DURATION_STRINGS[]; // = new String[]{"a second", "% seconds", "a minute", "% minutes", "a hour", "% hours", "a day", "% days", "a month", "% months", "a year", "% months", "a century", "% centuries", "a millennium", "% millennia"};
	public static String ARRAY_FILE_SIZES[]; // = new String[]{"% bytes", "%KB", "%MB", "%GB", "%TB"};

	public static final String MAYBE_NEVER_MATCH = "308rhtgwsrpifgj42witifglandlvzdlvligheslihfsienaifqpifjpw4jtigfpjepitgwsgvdzk;fngwrihgiwthio38y5801u41e-2dqw[ada]pvsjrgspihrsgendvkbdofehfgwrgr;grghl'd[hp]d[.,[/.,/.]jpdfgpihseougfewfl";
	public static final Pattern MAYBE_NEVER_MATCH_PATTERN = Pattern.compile("$^");
	public static final Pattern DISALLOWED_USERNAME_CHARACTERS = Pattern.compile("[^A-Za-z0-9_]");
	public static final char[] HANGUL_TO_HANJA_MAP = new char[65537];
	public static final String JAPANESE_SMALL_VOWELS = appendKatagana("[ぁぃぅぇぉゃゅょ]?");
	public static final String JAPANESE_LONG_PRONUNCIATION = "(?:[あいうえおー]?[んン]?|[るル]?)";
	public static final String REGEX_SPECIAL_CHARACTERS =".\\+*?[^]$(){}=!<>|:-";

	public static void initHanjaArray(final Resources res){
		if(HANGUL_TO_HANJA_MAP[65536] == 1)
			return;
		new Thread(){
			@Override
			public void run() {
				InputStream in = null;
				int i = 0;
				try{
					ByteBuffer bb =ByteBuffer.allocate(65536*2);
					in = res.getAssets().open("hanjahangul");
					in.read(bb.array());
					bb.rewind();
					for(; i < 65536; i++)
						HANGUL_TO_HANJA_MAP[i] = (char) bb.getShort();
					HANGUL_TO_HANJA_MAP[65536] = 1;
				}catch(Exception e){
					e.printStackTrace();
					for(; i < 65536; i++)
						HANGUL_TO_HANJA_MAP[i] = (char) i;
				}finally{
					StreamTools.close(in);
				}
			}
		}.start();
	}

	public static String fileSize(long nSize){
		double nS = nSize;
		if(nS < 1024)
			return ARRAY_FILE_SIZES[0].replace("%", Long.toString(nSize));
		if((nS = nS / 1024) < 1024)
			return ARRAY_FILE_SIZES[1].replace("%", BigDecimal.valueOf(nS).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
		if((nS = nS / 1024) < 1024)
			return ARRAY_FILE_SIZES[2].replace("%", BigDecimal.valueOf(nS).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
		if((nS = nS / 1024) < 1024)
			return ARRAY_FILE_SIZES[3].replace("%", BigDecimal.valueOf(nS).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
		return ARRAY_FILE_SIZES[4].replace("%", BigDecimal.valueOf(nS).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
	}

	/**
	 * Constant char array only consisting of alphanumeric characters
	 */
	private static final char[] RANDOM_CHARACTERS="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

	/**
	 * Decode the URL, including %u encodes.
	 * @param encoded Encoded string
	 * @return Decoded string
	 */
	public static String UrlDecode(String encoded){
		byte array[] = encoded.getBytes();
		StringBuilder decoded = new StringBuilder(encoded.length());
		int cursor = 0;
		while(cursor < array.length){
			if(array[cursor] == '%'){
				cursor++;
				int val = 0;
				for(int i = array[cursor] == 'u' ? 4 : 2; i>0; i--, cursor++)
					val = val * 16 + (array[cursor]>='0' && array[cursor]<='9' ? array[cursor]-'0' : array[cursor]-'A'+10);
				decoded.append((char)val);
			}else{
				decoded.append((char)array[cursor]);
				cursor++;
			}
		}
		return decoded.toString();
	}

	/**
	 * Encode the URL, according to RFC 3986
	 * @param component String to encode
	 * @return Encoded string
	 */
	public static String UrlEncode(String component){
		byte arr[] = component.getBytes();
		StringBuilder buffer = new StringBuilder();
		for(int i = 0; i < arr.length; i++){
			if((arr[i] >= 'a' && arr[i] <= 'z') || (arr[i] >= 'A' && arr[i] <= 'Z') || (arr[i] >= '0' && arr[i] <= '9') || arr[i] == '-' || arr[i] == '.' || arr[i] == '_' || arr[i] == '~')
				buffer.append((char)arr[i]);
			else
				buffer.append((arr[i]&0xF0)==0?"%0":"%").append(Integer.toHexString(0xFF & arr[i]).toUpperCase(Locale.ENGLISH));
		}
		return buffer.toString();
	}

	/**
	 * Generate cryptographically secure random alphanumeric string.
	 * @param length Length of the random string
	 * @return Generated random string
	 */
	public static String getSafeRandomString(int length){
		StringBuilder buffer = new StringBuilder();
		SecureRandom random = new SecureRandom();
		for(int i = 0; i < length; i++)
			buffer.append(RANDOM_CHARACTERS[random.nextInt(RANDOM_CHARACTERS.length)]);
		return buffer.toString();
	}

	/**
	 * Generate simple random alphanumeric string.
	 * @param length Length of the random string
	 * @return Generated random string
	 */
	public static String getRandomString(int length){
		StringBuilder buffer = new StringBuilder();
		Random random = new Random();
		for(int i = 0; i < length; i++)
			buffer.append(RANDOM_CHARACTERS[random.nextInt(RANDOM_CHARACTERS.length)]);
		return buffer.toString();
	}

	/**
	 * Calculates HMAC-SHA1 using given parameters
	 * @param baseString Base string
	 * @param keyString Key string
	 * @return Encoded byte array
	 */
	public static byte[] HmacSha1(String baseString, String keyString){
		try{
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(keyString.getBytes(), "HmacSHA1"));
			return mac.doFinal(baseString.getBytes());
		}catch(Exception e){
			return null;
		}
	}


	public static String fillStringResFormat(Context context, int resId, String... replacements){
		String[] s=context.getString(resId).split("\\$\\{");
		StringBuilder sb=new StringBuilder(s[0]);
		for(int i=1; i<s.length; i++){
			int j=0;
			for(; j<replacements.length; j+=2)
				if(s[i].startsWith(replacements[j]) && s[i].startsWith("}", replacements[j].length()))
					break;
			if(j >= replacements.length)
				sb.append("${").append(s[i]);
			else
				sb.append(replacements[j+1]).append(s[i], replacements[j].length()+1, s[i].length());
		}
		return sb.toString();
	}

	// http://codingdojang.com/scode/281
	public static String toHalfChar(String src){
		if (src == null)
			return null;
		StringBuilder strBuf = new StringBuilder();
		int nSrcLength = src.length();
		for (int i = 0; i < nSrcLength; i++){
			char c = src.charAt(i);
			if (c >= '！' && c <= '～')
				c -= 0xfee0;
			else if (c == '　')
				c = 0x20;
			strBuf.append(c);
		}
		return strBuf.toString();
	}

	public static String toFullChar(String src){
		if (src == null)
			return null;
		StringBuilder strBuf = new StringBuilder();
		int nSrcLength = src.length();
		for (int i = 0; i < nSrcLength; i++) {
			char c = src.charAt(i);
			if (c >= 0x21 && c <= 0x7e)
				c += 0xfee0;
			else if (c == 0x20)
				c = 0x3000;
			strBuf.append(c);
		}
		return strBuf.toString();
	}


	public static String DurationToDisplayTime(long nSeconds){
		if(nSeconds < 60){
			if(nSeconds <= 1)
				return ARRAY_RELATIVE_DURATION_STRINGS[0];
			else
				return ARRAY_RELATIVE_DURATION_STRINGS[1].replace("%", Long.toString(nSeconds));
		}else if(nSeconds < 60 * 60){
			if(nSeconds / 60 == 1)
				return ARRAY_RELATIVE_DURATION_STRINGS[2];
			else
				return ARRAY_RELATIVE_DURATION_STRINGS[3].replace("%", Long.toString(nSeconds / 60));
		}else if(nSeconds < 60 * 60 * 24){
			if(nSeconds / 60 / 60 == 1)
				return ARRAY_RELATIVE_DURATION_STRINGS[4];
			else
				return ARRAY_RELATIVE_DURATION_STRINGS[5].replace("%", Long.toString(nSeconds / 60 / 60));
		}else if(nSeconds < 60 * 60 * 24 * 30){
			if(nSeconds / 60 / 60 / 24 == 1)
				return ARRAY_RELATIVE_DURATION_STRINGS[6];
			else
				return ARRAY_RELATIVE_DURATION_STRINGS[7].replace("%", Long.toString(nSeconds / 60 / 60 / 24));
		}else if(nSeconds < 60 * 60 * 24 * 30 * 365){
			if(nSeconds / 60 / 60 / 24 / 30 == 1)
				return ARRAY_RELATIVE_DURATION_STRINGS[8];
			else
				return ARRAY_RELATIVE_DURATION_STRINGS[9].replace("%", Long.toString(nSeconds / 60 / 60 / 24 / 30));
		}else if(nSeconds < 60 * 60 * 24 * 30 * 365 * 100L){
			if(nSeconds / 60 / 60 / 24 / 30 / 365 == 1)
				return ARRAY_RELATIVE_DURATION_STRINGS[10];
			else
				return ARRAY_RELATIVE_DURATION_STRINGS[11].replace("%", Long.toString(nSeconds / 60 / 60 / 24 / 30 / 365));
		}else if(nSeconds < 60 * 60 * 24 * 30 * 365 * 1000L){
			if(nSeconds / 60 / 60 / 24 / 30 / 365 / 100 == 1)
				return ARRAY_RELATIVE_DURATION_STRINGS[12];
			else
				return ARRAY_RELATIVE_DURATION_STRINGS[13].replace("%", Long.toString(nSeconds / 60 / 60 / 24 / 30 / 365 / 100));
		}else if(nSeconds / 60 / 60 / 24 / 30 / 365 / 1000 == 1)
			return ARRAY_RELATIVE_DURATION_STRINGS[14];
		else
			return ARRAY_RELATIVE_DURATION_STRINGS[15].replace("%", Long.toString(nSeconds / 60 / 60 / 24 / 30 / 365 / 1000));
	}

	public static String UnixtimeToDisplayTime(long nUnixtime){
		long nRelativeTime = (System.currentTimeMillis() - nUnixtime) / 1000;
		// return ARRAY_RELATIVE_TIME_STRINGS[1].replace("%", Long.toString(nRelativeTime));
		// *
		if(nRelativeTime < 60){
			if(nRelativeTime <= 1)
				return ARRAY_RELATIVE_TIME_STRINGS[0];
			else
				return ARRAY_RELATIVE_TIME_STRINGS[1].replace("%", Long.toString(nRelativeTime));
		}else if(nRelativeTime < 60 * 60){
			if(nRelativeTime / 60 == 1)
				return ARRAY_RELATIVE_TIME_STRINGS[2];
			else
				return ARRAY_RELATIVE_TIME_STRINGS[3].replace("%", Long.toString(nRelativeTime / 60));
		}else if(nRelativeTime < 60 * 60 * 24){
			if(nRelativeTime / 60 / 60 == 1)
				return ARRAY_RELATIVE_TIME_STRINGS[4];
			else
				return ARRAY_RELATIVE_TIME_STRINGS[5].replace("%", Long.toString(nRelativeTime / 60 / 60));
		}else if(nRelativeTime < 60 * 60 * 24 * 30){
			if(nRelativeTime / 60 / 60 / 24 == 1)
				return ARRAY_RELATIVE_TIME_STRINGS[6];
			else
				return ARRAY_RELATIVE_TIME_STRINGS[7].replace("%", Long.toString(nRelativeTime / 60 / 60 / 24));
		}else if(nRelativeTime < 60 * 60 * 24 * 30 * 365){
			if(nRelativeTime / 60 / 60 / 24 / 30 == 1)
				return ARRAY_RELATIVE_TIME_STRINGS[8];
			else
				return ARRAY_RELATIVE_TIME_STRINGS[9].replace("%", Long.toString(nRelativeTime / 60 / 60 / 24 / 30));
		}else if(nRelativeTime < 60 * 60 * 24 * 30 * 365 * 100){
			if(nRelativeTime / 60 / 60 / 24 / 30 / 365 == 1)
				return ARRAY_RELATIVE_TIME_STRINGS[10];
			else
				return ARRAY_RELATIVE_TIME_STRINGS[11].replace("%", Long.toString(nRelativeTime / 60 / 60 / 24 / 30 / 365));
		}else if(nRelativeTime < 60 * 60 * 24 * 30 * 365 * 1000){
			if(nRelativeTime / 60 / 60 / 24 / 30 / 365 / 100 == 1)
				return ARRAY_RELATIVE_TIME_STRINGS[12];
			else
				return ARRAY_RELATIVE_TIME_STRINGS[13].replace("%", Long.toString(nRelativeTime / 60 / 60 / 24 / 30 / 365 / 100));
		}else if(nRelativeTime / 60 / 60 / 24 / 30 / 365 / 1000 == 1)
			return ARRAY_RELATIVE_TIME_STRINGS[14];
		else
			return ARRAY_RELATIVE_TIME_STRINGS[15].replace("%", Long.toString(nRelativeTime / 60 / 60 / 24 / 30 / 365 / 1000));
			//*/
	}


	private static String appendKatagana(String input){
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < input.length(); i++){
			out.append(input.charAt(i));
			char o = JapaneseCharacter.toKatakana(input.charAt(i));
			if(o!=input.charAt(i))
				out.append(o);
		}
		return out.toString();
	}
	public static String unHanja(String input){
		StringBuilder b = new StringBuilder();
		for(int i = 0; i < input.length(); i++)
			b.append(HANGUL_TO_HANJA_MAP[input.charAt(i)]);
		return b.toString();
	}

	public static String getReplacableCharacters(char c){
		StringBuilder p = new StringBuilder("[");
		if(REGEX_SPECIAL_CHARACTERS.contains(Character.toString(c)))
			p.append("\\");
		p.append(c);
		char code = HANGUL_TO_HANJA_MAP[c];
		if(code != c)
			p.append(code);
		p.append("]");
		switch(code){
			case 'ㄱ': p.append("|[r가-깋까-낗]"); code='g'; break;
			case 'ㄲ': p.append("|[r까-낗]"); code='g'; break;
			case 'ㄴ': p.append("|[s나-닣]"); code='n'; break;
			case 'ㄷ': p.append("|[e다-딯따-띻]"); code='d'; break;
			case 'ㄸ': p.append("|[e따-띻]"); code='d'; break;
			case 'ㄹ': p.append("|[f라-맇]"); code='r'; break;
			case 'ㅁ': p.append("|[a마-밓]"); code='m'; break;
			case 'ㅂ': p.append("|[q바-빟빠-삫]"); code='b'; break;
			case 'ㅃ': p.append("|[q빠-삫]"); code='b'; break;
			case 'ㅅ': p.append("|[t사-싷싸-앃]"); code='s'; break;
			case 'ㅆ': p.append("|[t싸-앃]"); code='s'; break;
			case 'ㅇ': p.append("|[d아-잏]").append(appendKatagana("|[あいうえおやゆよぁぃぅぇぉゃゅょ]")).append(JAPANESE_LONG_PRONUNCIATION); code='y'; break;
			case 'ㅈ': p.append("|[w자-짛짜-찧]"); code='z'; break;
			case 'ㅉ': p.append("|[w짜-찧]"); code='z'; break;
			case 'ㅊ': p.append("|[c차-칳]"); code='c'; break;
			case 'ㅋ': p.append("|[z카-킿]"); code='k'; break;
			case 'ㅌ': p.append("|[x타-팋]"); code='t'; break;
			case 'ㅍ': p.append("|[v파-핗]").append(appendKatagana("|[ふ]")).append(JAPANESE_SMALL_VOWELS).append(JAPANESE_LONG_PRONUNCIATION); code='p'; break; // p/f
			case 'ㅎ': p.append("|[g하-힣]"); code='h'; break;
			case 'ㅛ': p.append("|y"); break;
			case 'ㅕ': p.append("|u"); break;
			case 'ㅑ': p.append("|i"); break;
			case 'ㅐ': p.append("|o"); break;
			case 'ㅔ': p.append("|p"); break;
			case 'ㅗ': p.append("|h"); break;
			case 'ㅓ': p.append("|j"); break;
			case 'ㅏ': p.append("|k"); break;
			case 'ㅣ': p.append("|l"); break;
			case 'ㅠ': p.append("|b"); break;
			case 'ㅜ': p.append("|n"); break;
			case 'ㅡ': p.append("|m"); break;
		}
		if (Character.isWhitespace(code))
			p.append("\\s");
		boolean appendJVowels = true, isJapanese = true;
		switch(code){
			case 'a': p.append(appendKatagana("|[あぁ]")); appendJVowels=false; break;
			case 'b': p.append(appendKatagana("|[っ]?[ばびぶべぼ]")); break;
			case 'c': p.append(appendKatagana("|[っ]?[ち]")); break;
			case 'd': p.append(appendKatagana("|[っ]?[だづでど]")); break;
			case 'e': p.append(appendKatagana("|[えぇ]")); appendJVowels=false; break;
			case 'f': p.append(appendKatagana("|[ふ]")); break;
			case 'g': p.append(appendKatagana("|[っ]?[がぎぐげご]")); break;
			case 'h': p.append(appendKatagana("|[っ]?[はひふへほ]")); break;
			case 'i': p.append(appendKatagana("|[いぃ]")); appendJVowels=false; break;
			case 'j': p.append(appendKatagana("|[っ]?[ざじずぜぞ]")); break;
			case 'k': p.append(appendKatagana("|[っ]?[かきくけこ]")); break;
			case 'l': p.append(appendKatagana("|[っ]?[らりるれろ]")); break;
			case 'm': p.append(appendKatagana("|[っ]?[まみむめもん]")); break;
			case 'n': p.append(appendKatagana("|[っ]?[なにぬねのん]")); break;
			case 'o': p.append(appendKatagana("|[おぉ]")); appendJVowels=false; break;
			case 'p': p.append(appendKatagana("|[っ]?[ぱぴぷぺぽ]")); break;
			case 'r': p.append(appendKatagana("|[っ]?[らりるれろ]")); break;
			case 's': p.append(appendKatagana("|[っ]?[さしすせそ]")); break;
			case 't': p.append(appendKatagana("|[っ]?[たちつてと]")); break;
			case 'u': p.append(appendKatagana("|[うぅ]")); appendJVowels=false; break;
			case 'v': p.append(appendKatagana("|[っ]?[ヴ]")); break;
			case 'w': p.append(appendKatagana("|[わを]")); break;
			case 'y': p.append(appendKatagana("|[やゆよゃゅょ]")); appendJVowels=false; break;
			case 'z': p.append(appendKatagana("|[っ]?[ざじずぜぞ]")); break;
			case 'ー': p.append("|\\-"); appendJVowels = false; break;
			case '-': p.append("|_ー"); appendJVowels = false; break;
			case '_': p.append("|\\-"); appendJVowels = false; break;
			default: appendJVowels = false; isJapanese = false;
		}
		if(appendJVowels)
			p.append(JAPANESE_SMALL_VOWELS);
		if(isJapanese)
			p.append(JAPANESE_LONG_PRONUNCIATION);
		return p.toString();
	}
	public static Pattern getMaybePatternSearcher(String input){
		return getMaybePatternSearcher(input, true);
	}
	public static Pattern getMaybePatternSearcher(String input, boolean useInitialSearch){
		if(input.equals(MAYBE_NEVER_MATCH))
			return MAYBE_NEVER_MATCH_PATTERN;
		StringBuilder b = new StringBuilder("(");
		String lowercase = StringTools.toHalfChar(input.replaceAll("\\s+", " ").toLowerCase());
		for(int i = 0; i < lowercase.length(); i++){
			char c = lowercase.charAt(i);
			if(c == ' '){
				b.append(").*?(");
				continue;
			}
			b.append("(?:").append(getReplacableCharacters(c)).append(")\\s*?");
		}
		b.append(")");
		if(useInitialSearch){
			b.append("|");
			for(int i = 0; i < lowercase.length(); i++){
				char c = lowercase.charAt(i);
				if(c == ' '){
					b.append(".*?");
					continue;
				}
				if(Character.isLetterOrDigit(c))
					b.append("(?:\\b|(?<=_))(").append(getReplacableCharacters(c)).append(").*?");
				else if(REGEX_SPECIAL_CHARACTERS.contains(Character.toString(c)))
					b.append("(\\").append(c).append(").*");
				else
					b.append("(").append(c).append(").*");
			}
		}
		return Pattern.compile(b.toString(), Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
	}

}

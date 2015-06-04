package com.soreepeong.darknova.tools;

/**
 * Compilation of array functions.
 * <p/>
 * indexOf: referenced from http://stackoverflow.com/questions/21341027/find-indexof-a-byte-array-within-another-byte-array
 */
public class ArrayTools {

	/**
	 * Static utilities class.
	 */
	private ArrayTools() {
	}

	/**
	 * Prepare for {@code indexOf}
	 *
	 * @param pattern What will be searched.
	 * @return Computed failure function
	 */
	public static <_T> int[] computeFailure(_T[] pattern) {
		int[] failure = new int[pattern.length];
		for (int j = 0, i = 1; i < pattern.length; i++) {
			while (j > 0 && pattern[j] != pattern[i])
				j = failure[j - 1];
			if (pattern[j] == pattern[i])
				j++;
			failure[i] = j;
		}
		return failure;
	}

	/**
	 * Prepare for {@code indexOf}
	 *
	 * @param pattern What will be searched.
	 * @return Computed failure function
	 */
	public static int[] computeFailure(byte[] pattern) {
		int[] failure = new int[pattern.length];
		for (int j = 0, i = 1; i < pattern.length; i++) {
			while (j > 0 && pattern[j] != pattern[i])
				j = failure[j - 1];
			if (pattern[j] == pattern[i])
				j++;
			failure[i] = j;
		}
		return failure;
	}

	/**
	 * Search the haystack byte array for the first occurrence of the generics array needle within given boundaries.
	 *
	 * @param haystack The array to search in.
	 * @param start    First index in haystack.
	 * @param stop     Last index in haystack.
	 * @param needle   What is being searched.
	 * @param failure  Computed failure from {@code computeFailure}
	 * @return The position of where the needle exists relative to the beginning of the haystack string (independent of start). -1 if the needle was not found.
	 */
	public static <_T> int indexOf(_T[] haystack, int start, int stop, _T[] needle, int[] failure) {
		if (haystack == null || needle == null || failure == null)
			return -1;
		for (int j = 0, i = start; i < stop; i++) {
			while (j > 0 && needle[j] != haystack[i])
				j = failure[j - 1];
			if (needle[j] == haystack[i])
				j++;
			if (j == needle.length)
				return i - needle.length + 1;
		}
		return -1;
	}

	/**
	 * Search the haystack byte array for the first occurrence of the byte array needle within given boundaries.
	 *
	 * @param haystack The array to search in.
	 * @param start    First index in haystack.
	 * @param stop     Last index in haystack.
	 * @param needle   What is being searched.
	 * @param failure  Computed failure from {@code computeFailure}
	 * @return The position of where the needle exists relative to the beginning of the haystack string (independent of start). -1 if the needle was not found.
	 */
	public static int indexOf(byte[] haystack, int start, int stop, byte[] needle, int[] failure) {
		if (haystack == null || needle == null || failure == null)
			return -1;
		for (int j = 0, i = start; i < stop; i++) {
			while (j > 0 && needle[j] != haystack[i])
				j = failure[j - 1];
			if (needle[j] == haystack[i])
				j++;
			if (j == needle.length)
				return i - needle.length + 1;
		}
		return -1;
	}
}

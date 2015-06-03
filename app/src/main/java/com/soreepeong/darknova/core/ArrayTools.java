package com.soreepeong.darknova.core;

import java.util.HashSet;
import java.util.List;

/**
 * Created by Soreepeong on 2015-04-27.
 */
public class ArrayTools {

	public static int[] indexOfFailureFunction(byte[] pattern){
		int[] failure = new int[pattern.length];
		for(int j=0, i = 1; i < pattern.length; i++){
			while(j > 0 && pattern[j] != pattern[i])
				j = failure[j - 1];
			if(pattern[j] == pattern[i])
				j++;
			failure[i] = j;
		}
		return failure;
	}

	public static int indexOf(byte[] data, int start, int stop, byte[] pattern, int[] failure){
		if(data == null || pattern == null || failure == null)
			return -1;
		for(int j=0, i = start; i < stop; i++){
			while(j > 0 && pattern[j] != data[i])
				j = failure[j - 1];
			if(pattern[j] == data[i])
				j++;
			if(j == pattern.length)
				return i - pattern.length + 1;
		}
		return -1;
	}
}

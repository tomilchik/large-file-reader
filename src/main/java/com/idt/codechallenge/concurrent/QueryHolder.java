package com.idt.codechallenge.concurrent;

import java.util.List;
import java.util.Set;

/**
 * Interface implemented by a holder of list of queries.
 * @author leonidtomilchik
 *
 */
public interface QueryHolder {
	
	public List<Set<String>> getQueries();

}

package nl.cochez.query_processing.metadata;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.jena.atlas.io.IndentedLineBuffer;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Node_Variable;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpExtend;
import org.apache.jena.sparql.algebra.op.OpGroup;
import org.apache.jena.sparql.algebra.op.OpSlice;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.apache.jena.sparql.algebra.table.TableData;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingBuilder;
import org.apache.jena.sparql.expr.E_Equals;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.lang.sparql_11.ParseException;
import org.apache.jena.sparql.serializer.FormatterElement;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementVisitorBase;
import org.apache.jena.sparql.syntax.ElementBind;
import org.apache.jena.sparql.syntax.ElementData;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import me.tongfei.progressbar.ProgressBar;

import org.apache.jena.arq.querybuilder.AskBuilder;
import org.apache.jena.arq.querybuilder.ConstructBuilder;
import org.apache.jena.arq.querybuilder.DescribeBuilder;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.arq.querybuilder.handlers.HandlerBlock;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;

import nl.cochez.query_processing.metadata.IsomorpismClusteringQueryCollector.Node;
import nl.cochez.query_processing.metadata.OpAsQuery.Converter;

import java.io.IOException;

public class PatternDisplay {
    public static void rankPattern(ArrayList<Query> queryList, int top,int offset, int tripleNumber, boolean checkEndpoint, String sparqlendpoint, String dict_name,List<String> entity_rank_list, List<String> stop_list, List<String> ptop_List, List<String> otop_list, List<String> typetop_list) {
		// List<Query> pattern_query = new ArrayList<Query>();
		double threshold_subject = 1.0; // change the threshold for ratio
		double threshold_predicate = 1.0; // change the threshold for ratio
		double threshold_object = 1.0; // change the threshold for ratio
		double threshold_type = 1.0; // change the threshold for ratio
		int valid_top = 50; // the number of queries that will be considered in each functions
		List<Query> invalid_pattern_query = new ArrayList<Query>(); // the list of invalid pattern queries
		Map<Query,Double> dict_informativeness = new HashMap<Query,Double>(); // the map of query and its informativeness
		Map<Query,Boolean> dict_query = getDict(dict_name); // the map of query and its validity
		Graph<Query, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class); // the graph of pattern and query
		HashMap<Query, HashMultiset<Query>> pattern_instance = new HashMap<Query, HashMultiset<Query>>(); // hashmap for pattern and a set of unique queries for this pattern
		Graph<Query, DefaultEdge> pattern_query_graph = new DefaultDirectedGraph<Query, DefaultEdge>(DefaultEdge.class); // the graph of pattern and query
		Map<Query, Integer> patter_length_map = new HashMap<Query,Integer>(); // the map of pattern and its length
		Map<Integer, Integer> pattern_numbers = new HashMap<Integer, Integer>(); // the map of pattern length and its frequency
		Map<Integer, Integer> instance_numbers = new HashMap<Integer, Integer>(); // the map of instance length and its frequency
		Map<String, Query> query_type = new HashMap<String, Query>(); // the map of instance and its type
		HashMap<String, HashMultiset<Query>> iri_query = new HashMap<String, HashMultiset<Query>>(); // the map of iri and its queries
		List<String> type_counting_list = new ArrayList<String>(); // the list of type counting
		HashMap<Query, Integer> instance_freq = sortInstanceByValue(findFrequentQuery(queryList)); // get unique query list and the frequency of each unique query
		
		// comment of following code: get the frequency of each pattern
		try {
			BufferedWriter bw1 = new BufferedWriter(new FileWriter("unique_query_frequency.csv",true));
			for(Entry<Query,Integer> uqf:instance_freq.entrySet()){
				bw1.write(uqf.getKey().serialize().replace("\r", "\\r").replace("\n", "\\n")+" & "+Integer.toString(uqf.getValue()));
				bw1.newLine();
				bw1.flush();
			}
			bw1.close();
		} catch (Exception e) {
			// TODO: handle exception
		}

		List<Query> valid_unique_query = new ArrayList<Query>(); // the list of valid unique queries
		valid_unique_query.addAll(instance_freq.keySet()); // add all unique queries to the list

		double sum_info_func1 = 0.0; // the sum of informativeness of function 1
		double sum_info_func2 = 0.0; // the sum of informativeness of function 2
		double sum_info_func3 = 0.0; // the sum of informativeness of function 3
		double sum_info_func4 = 0.0; // the sum of informativeness of function 4

		double cc1 = 0.0; // the number of complexity coverage of function 1
		double cc2 = 0.0; // the number of complexity coverage of function 2
		double cc3 = 0.0; // the number of complexity coverage of function 3
		double cc4 = 0.0; // the number of complexity coverage of function 4

		Set<Integer> complex = new HashSet<Integer>(); // the set of complexity, used to count the total number of complexity
		

		try {
			BufferedWriter bw_top_valid = new BufferedWriter(new FileWriter("function1.txt",true)); // the file to store the top valid queries of function 1
			BufferedWriter bw_func_statistics = new BufferedWriter(new FileWriter("function1_statistiscs.txt",true)); // the file to store the random 50 queries of function 4
			if (checkEndpoint) { // if checkEndpoint is true, then we need to check the validity of queries
				List<Double> func1scores = new ArrayList<Double>(); // the list of informativeness of function 1
				Set<Integer> complexities = new HashSet<Integer>(); // the set of complexity, used to count the number of complexity
				ProgressBar pb = new ProgressBar("Finding top-" + valid_top + " valid of unique queries: ", valid_top); // the progress bar
				int count = 0; // count for only select 50 queries
				for (Query uniQuery : instance_freq.keySet()) { // for each unique query
					if (count >= valid_top) // if the number of valid queries is more than valid_top, then break
						break;
					if (StoreOrRead(uniQuery, dict_query, sparqlendpoint, dict_name)) { // if the query is valid
						complexities.add(getBGPtripleNumber(uniQuery)); // add the complexity to the set
						double score = informativeness(uniQuery); // get the informativeness of the query
						func1scores.add(score); // add the informativeness to the list
						bw_top_valid.write(uniQuery.serialize().replace("\r", "\\r").replace("\n", "\\n")); // write the query to the file
						bw_top_valid.newLine(); // write a new line
						bw_top_valid.flush(); // flush the buffer
						pb.step(); // step the progress bar
						count ++;
					}
				}
				double[] scores = new double[func1scores.size()]; // the array of informativeness of function 1
				for (int i = 0; i < func1scores.size(); i++) // for each informativeness
					scores[i] = func1scores.get(i); // add the informativeness to the array
				for(double s :ZScore(scores)){ // for each normalized informativeness
					sum_info_func1+=s; // add the normalized informativeness to the sum
				}
				if (func1scores.size() != 50)
					System.err.println("The number of funciton 1 outputs is not 50!");
					bw_func_statistics.write("Informativeness list:");
					bw_func_statistics.newLine();
					bw_func_statistics.write(func1scores.toString()); // write the informativeness list to the file
					bw_func_statistics.newLine();
					bw_func_statistics.write("Sum of informativeness:"+ sum_info_func1);// write the sum of informativeness to the file
					bw_func_statistics.newLine();
					bw_func_statistics.write("Normalized Informativeness list:");
					bw_func_statistics.newLine();
					bw_func_statistics.write(Arrays.toString(ZScore(scores)) + scores.length); // write the normalized informativeness list to the file
					bw_func_statistics.newLine();
					bw_func_statistics.write("Complexity count:"); // write the complexity count to the file
					bw_func_statistics.newLine();
					bw_func_statistics.write(Integer.toString(complexities.size()));
				cc1 = complexities.size();
				bw_top_valid.flush();
				pb.close();
			}
			bw_top_valid.close();
			bw_func_statistics.close();
		} catch (Exception e) {
			// TODO: handle exception
			System.err.println("Error in function 1!");
		}

		// try {
		// 	BufferedWriter bw_valid = new BufferedWriter(new FileWriter("unique_valid_query_frequency.csv",true));
		// 	for(Query uqf:valid_unique_query){
		// 		bw_valid.write(uqf.serialize().replace("\r", "\\r").replace("\n", "\\n")+" & "+Integer.toString(instance_freq.get(uqf)));
		// 		bw_valid.newLine();
		// 		bw_valid.flush();
		// 	}
		// 	bw_valid.close();
		// } catch (Exception e) {
		// 	// TODO: handle exception
		// }

		Collections.shuffle(valid_unique_query); // shuffle the valid unique queries
		try {
			BufferedWriter bw_random50 = new BufferedWriter(new FileWriter("function4_queries.txt",true)); // the file to store the random 50 queries of function 4
			BufferedWriter bw_func_statistics = new BufferedWriter(new FileWriter("function4_statistiscs.txt",true)); // the file to store the random 50 queries of function 4
			List<Double> func4scores = new ArrayList<Double>(); // the list of informativeness of function 4
			Set<Integer> complexities = new HashSet<Integer>(); // the set of complexity, used to count the number of complexity
			int random_count = 0;
			for(Query uqf:valid_unique_query){ // for each unique query
				if(random_count>=50) // if the number of random queries is more than 50, then stop
					break;
				if (StoreOrRead(uqf, dict_query, sparqlendpoint, dict_name)){ // if the query is valid
					complexities.add(getBGPtripleNumber(uqf)); // add the complexity to the set
					double score = informativeness(uqf); // get the informativeness of the query
					func4scores.add(score); // add the informativeness to the list
					bw_random50.write(uqf.serialize().replace("\r", "\\r").replace("\n", "\\n")); // write the query to the file
					bw_random50.newLine(); // write a new line
					bw_random50.flush(); // flush the buffer
					random_count ++;
				}
			}
			double[] scores = new double[func4scores.size()]; // the array of informativeness of function 4
			for (int i = 0; i < func4scores.size(); i++) // for each informativeness
				scores[i] = func4scores.get(i); // add the informativeness to the array
			for(double s :ZScore(scores)){ // for each normalized informativeness
				sum_info_func4+=s; // add the normalized informativeness to the sum
			}
			if (func4scores.size() != 50)
					System.err.println("The number of funciton 4 outputs is not 50!");
					bw_func_statistics.write("Informativeness list:");
					bw_func_statistics.newLine();	
					bw_func_statistics.write(func4scores.toString()); // write the informativeness list to the file
					bw_func_statistics.newLine();
					bw_func_statistics.write("Sum of informativeness:"+ sum_info_func4);// write the sum of informativeness to the file
					bw_func_statistics.newLine();
					bw_func_statistics.write("Normalized Informativeness list:");
					bw_func_statistics.newLine();
					bw_func_statistics.write(Arrays.toString(ZScore(scores)) + scores.length); // write the normalized informativeness list to the file
					bw_func_statistics.newLine();
					bw_func_statistics.write("Complexity count:");
					bw_func_statistics.newLine();
					bw_func_statistics.write(Integer.toString(complexities.size())); // write the complexity count to the file
			cc4 = complexities.size();
			bw_random50.flush();
			bw_random50.close();
			bw_func_statistics.close();
		} catch (Exception e) {
			// TODO: handle exception
			System.err.println("Error in function 4!");
		}


		br1: for (Query q : valid_unique_query) { // for each unique query
			// System.out.println(q.queryType().name());
			List<Triple> triples = new ArrayList<Triple>(); // the list of triples
			Map<String, String> replace_map = new HashMap<String, String>(); // the map of string and its replacement
			Set<String> var_set = new HashSet<String>(); // the set of variables
			Set<String> entity_set = new HashSet<String>(); // the set of entities
			Set<String> literal_set = new HashSet<String>(); // the set of literals
			Set<String> predicate_set = new HashSet<String>(); // the set of predicates
			Set<Long> number_set = new HashSet<Long>(); // the set of numbers
			Set<String> bind_set = get_bind_vars(q); // the set of bind variables
			Set<String> values_set = new HashSet<String>(); // the set of values
			Set<String> no_change_set = new HashSet<String>(); // the set of no change variables, which is in predicate position
			// Set<String> count_set = new HashSet<String>();
			Map<String, Integer> count_count = new HashMap<String, Integer>(); // the map of count and its frequency
			HashMap<Var, Var> extend_dict = new HashMap<Var, Var>(); // the map of extend and its variable
			Op ope = null;
			try {
				ope = Algebra.compile(q); // compile the query
			} catch (Exception e) {
				//TODO: handle exception
				continue br1;
			}
			// List<Triple> triple_list = new ArrayList<Triple>();
			AllOpVisitor allbgp = new AllOpVisitor() { // the visitor to visit all the op
				@Override
				public void visit(OpBGP opBGP) { // visit the opBGP, which is the basic pattern
					for (Triple t : opBGP.getPattern()) {
						triples.add(t); // add the triple to the list
						if (t.getPredicate().toString().equals("rdf:type") || t.getPredicate().toString().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") || t.getPredicate().toString().equals("a") || t.getPredicate().toString().equals("http://www.wikidata.org/prop/qualifier/P31")){
							no_change_set.add(t.getObject().toString()); // add the object to the no change set
							query_type.put(t.getObject().toString(),q); // add the object and its type to the map
							type_counting_list.add(t.getObject().toString()); // add the object to the type counting list
						}
						if (t.getSubject().isVariable() || t.getSubject().toString().startsWith("?")) { // if the subject is a variable
							var_set.add(t.getSubject().toString()); // add the subject to the variable set
						}
						if (t.getObject().isVariable() || t.getObject().toString().startsWith("?")) { // if the object is a variable
							var_set.add(t.getObject().toString()); // add the object to the variable set
						}
						if (t.getPredicate().isVariable() || t.getPredicate().toString().startsWith("?")) { // if the predicate is a variable
							predicate_set.add(t.getPredicate().toString()); // add the predicate to the predicate set
						}
						if (t.getSubject().isURI() || t.getSubject().isBlank()) { // if the subject is a URI or blank node
							entity_set.add(t.getSubject().toString()); // add the subject to the entity set
						}
						if (t.getObject().isURI() || t.getObject().isBlank()) { // if the object is a URI or blank node
							if(!no_change_set.contains(t.getObject().toString())) // if the object is not in the no change set
								entity_set.add(t.getObject().toString()); // add the object to the entity set
						}
						if (t.getSubject().isLiteral()){ // if the subject is a literal
							literal_set.add(t.getSubject().getLiteralLexicalForm()); // add the subject to the literal set
						}
						if (t.getObject().isLiteral()){ // if the object is a literal
							literal_set.add(t.getObject().getLiteralLexicalForm()); // add the object to the literal set
						}

						
						
					}
				}

				@Override
				public void visit(OpSlice opSlice) {
					// TODO Auto-generated method stub
					number_set.add(opSlice.getStart()); // add the start number to the number set
					number_set.add(opSlice.getLength()); // add the length number to the number set
					opSlice.getSubOp().visit(this);
				}

				public void visit(OpExtend opExtend){
					for(Var var : opExtend.getVarExprList().getExprs().keySet()){ // for each variable in the extend
						try {
							extend_dict.put(opExtend.getVarExprList().getExprs().get(var).asVar(), var); // add the variable and its extend to the map
						} catch (Exception e) {
							//TODO: handle exception
						}
						
					}
					opExtend.getSubOp().visit(this);
				}
	
				@Override
				public void visit(OpGroup opGroup){
					for(ExprAggregator exp : opGroup.getAggregators()){ // for each aggregator
						// System.out.println(extend_dict.get(exp.getVar()).getVarName()+" "+exp.getAggregator().getName().toLowerCase()); 
						try {
							if(count_count.containsKey(exp.getAggregator().getName().toLowerCase())){ // if the count map contains the aggregator
								count_count.put(exp.getAggregator().getName().toLowerCase(), count_count.get(exp.getAggregator().getName().toLowerCase())+1); // add the frequency of the aggregator
								replace_map.put("?"+extend_dict.get(exp.getVar()).getVarName(), "?"+exp.getAggregator().getName().toLowerCase()+Integer.toString(count_count.get(exp.getAggregator().getName().toLowerCase()))); // add the aggregator and its replacement to the map
							}
							else{
								count_count.put(exp.getAggregator().getName().toLowerCase(), 1); // add the aggregator and its frequency to the map
								replace_map.put("?"+extend_dict.get(exp.getVar()).getVarName(), "?"+exp.getAggregator().getName().toLowerCase()+Integer.toString(count_count.get(exp.getAggregator().getName().toLowerCase()))); // add the aggregator and its replacement to the map
							}
						} catch (Exception e) {
							//TODO: handle exception
						}
					}
					opGroup.getSubOp().visit(this);
				}
	
				@Override
				public void visit(OpTable opTable){
					// Iterator<Binding> rows = opTable.getTable().rows();
					// for(;rows.hasNext();){
					// 	Binding row = rows.next();
					// System.out.println(row.vars().next()+" "+row.get(row.vars().next()).toString());
					// }
				}
			};
			ope.visit(allbgp);

			// comment of following code: consider entity or literal in filter
			try {
				Element pattern = q.getQueryPattern();
				ElementVisitorBase filtervisitor = new ElementVisitorBase() {
					@Override
					public void visit(ElementFilter el) {
						Expr filterExpression = el.getExpr();
						if (filterExpression instanceof E_Equals) {
							E_Equals equals = (E_Equals) filterExpression;
							Expr right = equals.getArg2();
							if (right instanceof NodeValue) {
								NodeValue nodeValue = (NodeValue) right;
								if (nodeValue.isIRI()) {
									entity_set.add(nodeValue.asNode().getURI());
									// System.out.println("Filter URI: " + nodeValue.asNode().getURI());
								} else if (nodeValue.isLiteral()) {
									literal_set.add(nodeValue.asQuotedString());
									// System.out.println("Filter Literal: " + nodeValue.asQuotedString());
								}
							}
						}
					}
				};
				pattern.visit(filtervisitor);
			} catch (Exception e) {
				// TODO: handle exception
			}
			

			double new_score = entity_vairable_score(triples); // get the informativeness of the query

			//following code: store the query and its informativeness to file
			try {
				BufferedWriter bw_ratio = new BufferedWriter(new FileWriter("query_ratio.txt",true));
				bw_ratio.write(q.serialize()+" & "+Double.toString(new_score));
				bw_ratio.newLine();
				bw_ratio.flush();	
				bw_ratio.close();
			} catch (Exception e) {
				// TODO: handle exception
			}

			for (Triple t : triples) { // for each triple
				if(entity_rank_list.contains(t.getSubject().toString()) || entity_rank_list.contains(t.getPredicate().toString()) || entity_rank_list.contains(t.getObject().toString())){
					// if (new_score > threshold_subject) { // if the informativeness is more than the threshold
						if (iri_query.containsKey(t.getSubject().toString())) { // if the iri is in the map

							if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name)) // if the query is valid
								iri_query.get(t.getSubject().toString()).add(construcQuery_removeLimitOffset(q)); // add the query to the set
						} else {
							iri_query.put(t.getSubject().toString(), HashMultiset.create()); // add the iri and its set to the map
							if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name)) // if the query is valid
								iri_query.get(t.getSubject().toString()).add(construcQuery_removeLimitOffset(q)); // add the query to the set
						}
					// }
				}

				// if (stop_list.contains(t.getSubject().toString())) { // if the subject is in the sub/obj/predicate/type list
				// 	if (new_score > threshold_subject) { // if the informativeness is more than the threshold
				// 		if (iri_query.containsKey(t.getSubject().toString())) { // if the iri is in the map

				// 			if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name)) // if the query is valid
				// 				iri_query.get(t.getSubject().toString()).add(construcQuery_removeLimitOffset(q)); // add the query to the set
				// 		} else {
				// 			iri_query.put(t.getSubject().toString(), HashMultiset.create()); // add the iri and its set to the map
				// 			if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name)) // if the query is valid
				// 				iri_query.get(t.getSubject().toString()).add(construcQuery_removeLimitOffset(q)); // add the query to the set
				// 		}
				// 	}
				// }

				// if (ptop_List.contains(t.getPredicate().toString())) { // if the predicate is in the sub/obj/predicate/type list
				// 	if (new_score > threshold_subject) {
				// 		if (iri_query.containsKey(t.getPredicate().toString())) {

				// 			if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name))
				// 				iri_query.get(t.getPredicate().toString()).add(construcQuery_removeLimitOffset(q));

				// 		} else {
				// 			iri_query.put(t.getPredicate().toString(), HashMultiset.create());
				// 			if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name))
				// 				iri_query.get(t.getPredicate().toString()).add(construcQuery_removeLimitOffset(q));
				// 		}
				// 	}
				// }

				// if (otop_list.contains(t.getObject().toString())) { // if the object is in the sub/obj/predicate/type list
				// 	if (new_score > threshold_subject) {
				// 		if (iri_query.containsKey(t.getObject().toString())) {
				// 			if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name))
				// 				iri_query.get(t.getObject().toString()).add(construcQuery_removeLimitOffset(q));

				// 		} else {
				// 			iri_query.put(t.getObject().toString(), HashMultiset.create());
				// 			if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name))
				// 				iri_query.get(t.getObject().toString()).add(construcQuery_removeLimitOffset(q));
				// 		}
				// 	}
				// }

				// if (typetop_list.contains(t.getObject().toString())) { // if the object is in the sub/obj/predicate/type list
				// 	if (new_score > threshold_subject) {
				// 		if (iri_query.containsKey(t.getObject().toString())) {
				// 			if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name))
				// 				iri_query.get(t.getObject().toString()).add(construcQuery_removeLimitOffset(q));
				// 		}
				// 	} else {
				// 		iri_query.put(t.getObject().toString(), HashMultiset.create());
				// 		if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name))
				// 			iri_query.get(t.getObject().toString()).add(construcQuery_removeLimitOffset(q));
				// 	}
				// }
			}

			// following code: generate string of query under transfring to pattern
			String replace_query_string = "";
			try {
				replace_query_string = q.serialize();
			} catch (Exception e) {
				continue;
			}

			// following code: generating pattern query
			List<String> ent_vars = new ArrayList<String>();
			List<String> lit_vars = new ArrayList<String>();
			int count = 1;
			for (String var : var_set) {
				replace_map.put(var, "?variable" + Integer.toString(count++));
			}
			count = 1;
			for (String ent : entity_set){
				replace_map.put("<"+ent+">", "?ent" + Integer.toString(count));
				ent_vars.add("?ent" + Integer.toString(count++));
			}
			count = 1;
			for (String literal : literal_set){
				replace_map.put("\""+literal+"\"", "?str" + Integer.toString(count));
				replace_map.put(literal, "?str" + Integer.toString(count));
				lit_vars.add("?str" + Integer.toString(count++));
			}
			count = 1;
			for (String predicate : predicate_set){
				replace_map.put(predicate, "?predicate" + Integer.toString(count++));
			}
			count = 1;
			for (Long num : number_set)
				replace_query_string = replace_query_string.replace(" " + Long.toString(num), " 1");
			count = 1;
			for(String bind: bind_set){
				replace_map.put("?"+bind, "?bind" + Integer.toString(count++));
			}
			for (String var : replace_map.keySet()) {
				replace_query_string = replace_query_string.replace(var + " ", replace_map.get(var) + " ")
						.replace(var + "\n", replace_map.get(var) + "\n").replace(var + ")", replace_map.get(var) + ")")
						.replace(var + "\r", replace_map.get(var) + "\r").replace(var + ",", replace_map.get(var) + ",")
						.replace("<" + var + ">", "<" + replace_map.get(var) + ">").replace(var+"}", replace_map.get(var)+"}");
			}

			if(q.isSelectType()){
				SelectBuilder selectBuilder = new SelectBuilder();
				try {
					HandlerBlock handlerBlock = new HandlerBlock(QueryFactory.create(replace_query_string));
					selectBuilder.getHandlerBlock().addAll(handlerBlock);
					selectBuilder.setBase(null);
					for (Entry<String,String> prefix :q.getPrefixMapping().getNsPrefixMap().entrySet()){
						selectBuilder.addPrefix(prefix.getKey(), prefix.getValue());
					}
					for(String ent : ent_vars){
						try {
							selectBuilder.addFilter("isIRI("+ent+")");
						} catch (ParseException e) {
						}
					}
					for (String literal : lit_vars){
						try {
							selectBuilder.addFilter("isLiteral("+literal+")");
						} catch (ParseException e) {
						}
					}
					Query pattern_q = selectBuilder.build();
					// pattern_q = generalize_VALUES(pattern_q);
					if(pattern_instance.containsKey(pattern_q)){
						pattern_instance.get(pattern_q).add(q);
					}
					else{
						pattern_instance.put(pattern_q, HashMultiset.create());
						pattern_instance.get(pattern_q).add(q);
					}
					graph.addVertex(pattern_q);
					graph.addVertex(q);
					graph.addEdge(pattern_q, q);
					invalid_pattern_query.add(pattern_q);
					patter_length_map.put(pattern_q, triples.size());
					pattern_query_graph.addVertex(q);
					pattern_query_graph.addVertex(pattern_q);
					pattern_query_graph.addEdge(pattern_q, q);
					// if(!pattern_instance_pair.containsKey(pattern_q)){
					// 	if(checkEndpoint){
					// 		if(StoreOrRead(q,dict_query)){
					// 			pattern_query.add(pattern_q);
					// 			pattern_instance_pair.put(pattern_q, q);
					// 			// patter_length_map.put(pattern_q, triples.size());
					// 		}
					// 	}
					// 	else{
					// 		pattern_instance_pair.put(pattern_q, q);
					// 		// patter_length_map.put(pattern_q, triples.size());
					// 	}
					// }
				} catch (Exception e) {
					//TODO: handle exception
					continue br1;
				}
			}
			else if (q.isConstructType()){
				ConstructBuilder selectBuilder = new ConstructBuilder();
				try {
					HandlerBlock handlerBlock = new HandlerBlock(QueryFactory.create(replace_query_string));
					selectBuilder.getHandlerBlock().addAll(handlerBlock);
					selectBuilder.setBase(null);
					for (Entry<String,String> prefix :q.getPrefixMapping().getNsPrefixMap().entrySet()){
						selectBuilder.addPrefix(prefix.getKey(), prefix.getValue());
					}
					for(String ent : ent_vars){
						try {
							selectBuilder.addFilter("isIRI("+ent+")");
						} catch (ParseException e) {
						}
					}
					for (String literal : lit_vars){
						try {
							selectBuilder.addFilter("isLiteral("+literal+")");
						} catch (ParseException e) {
						}
					}
					Query pattern_q = selectBuilder.build();
					// pattern_q = generalize_VALUES(pattern_q);
					if(pattern_instance.containsKey(pattern_q)){
						pattern_instance.get(pattern_q).add(q);
					}
					else{
						pattern_instance.put(pattern_q, HashMultiset.create());
						pattern_instance.get(pattern_q).add(q);
					}
					graph.addVertex(pattern_q);
					graph.addVertex(q);
					graph.addEdge(pattern_q, q);
					invalid_pattern_query.add(pattern_q);
					patter_length_map.put(pattern_q, triples.size());
					pattern_query_graph.addVertex(q);
					pattern_query_graph.addVertex(pattern_q);
					pattern_query_graph.addEdge(pattern_q, q);
					// if(!pattern_instance_pair.containsKey(pattern_q)){
					// 	if(checkEndpoint){
					// 		if(StoreOrRead(q,dict_query)){
					// 			pattern_query.add(pattern_q);
					// 			pattern_instance_pair.put(pattern_q, q);
					// 			// patter_length_map.put(pattern_q, triples.size());
					// 		}
					// 	}
					// 	else{
					// 		pattern_instance_pair.put(pattern_q, q);
					// 		// patter_length_map.put(pattern_q, triples.size());
					// 	}
					// }
				} catch (Exception e) {
					//TODO: handle exception
					continue br1;
				}
			}
			else if (q.isAskType()){
				AskBuilder selectBuilder = new AskBuilder();
				try {
					HandlerBlock handlerBlock = new HandlerBlock(QueryFactory.create(replace_query_string));
					selectBuilder.getHandlerBlock().addAll(handlerBlock);
					selectBuilder.setBase(null);
					for (Entry<String,String> prefix :q.getPrefixMapping().getNsPrefixMap().entrySet()){
						selectBuilder.addPrefix(prefix.getKey(), prefix.getValue());
					}
					for(String ent : ent_vars){
						try {
							selectBuilder.addFilter("isIRI("+ent+")");
						} catch (ParseException e) {
						}
					}
					for (String literal : lit_vars){
						try {
							selectBuilder.addFilter("isLiteral("+literal+")");
						} catch (ParseException e) {
						}
					}
					Query pattern_q = selectBuilder.build();
					// pattern_q = generalize_VALUES(pattern_q);
					if(pattern_instance.containsKey(pattern_q)){
						pattern_instance.get(pattern_q).add(q);
					}
					else{
						pattern_instance.put(pattern_q, HashMultiset.create());
						pattern_instance.get(pattern_q).add(q);
					}
					graph.addVertex(pattern_q);
					graph.addVertex(q);
					graph.addEdge(pattern_q, q);
					invalid_pattern_query.add(pattern_q);
					patter_length_map.put(pattern_q, triples.size());
					pattern_query_graph.addVertex(q);
					pattern_query_graph.addVertex(pattern_q);
					pattern_query_graph.addEdge(pattern_q, q);
					// if(!pattern_instance_pair.containsKey(pattern_q)){
					// 	if(checkEndpoint){
					// 		if(StoreOrRead(q,dict_query)){
					// 			pattern_query.add(pattern_q);
					// 			pattern_instance_pair.put(pattern_q, q);
					// 			// patter_length_map.put(pattern_q, triples.size());
					// 		}
					// 	}
					// 	else{
					// 		pattern_instance_pair.put(pattern_q, q);
					// 		// patter_length_map.put(pattern_q, triples.size());
					// 	}
					// }
				} catch (Exception e) {
					continue br1;
				}
			}
			else if (q.isDescribeType()){
				DescribeBuilder selectBuilder = new DescribeBuilder();
				try {
					HandlerBlock handlerBlock = new HandlerBlock(QueryFactory.create(replace_query_string));
					selectBuilder.getHandlerBlock().addAll(handlerBlock);
					selectBuilder.setBase(null);
					for (Entry<String,String> prefix :q.getPrefixMapping().getNsPrefixMap().entrySet()){
						selectBuilder.addPrefix(prefix.getKey(), prefix.getValue());
					}
					for(String ent : ent_vars){
						try {
							selectBuilder.addFilter("isIRI("+ent+")");
						} catch (ParseException e) {
						}
					}
					for (String literal : lit_vars){
						try {
							selectBuilder.addFilter("isLiteral("+literal+")");
						} catch (ParseException e) {
						}
					}
					Query pattern_q = selectBuilder.build();
					// pattern_q = generalize_VALUES(pattern_q);
					if(pattern_instance.containsKey(pattern_q)){
						pattern_instance.get(pattern_q).add(q);
					}
					else{
						pattern_instance.put(pattern_q, HashMultiset.create());
						pattern_instance.get(pattern_q).add(q);
					}
					graph.addVertex(pattern_q);
					graph.addVertex(q);
					graph.addEdge(pattern_q, q);
					invalid_pattern_query.add(pattern_q);
					patter_length_map.put(pattern_q, triples.size());
					pattern_query_graph.addVertex(q);
					pattern_query_graph.addVertex(pattern_q);
					pattern_query_graph.addEdge(pattern_q, q);
					// if(!pattern_instance_pair.containsKey(pattern_q)){
					// 	if(checkEndpoint){
					// 		if(StoreOrRead(q,dict_query)){
					// 			pattern_query.add(pattern_q);
					// 			pattern_instance_pair.put(pattern_q, q);
					// 			// patter_length_map.put(pattern_q, triples.size());
					// 		}
					// 	}
					// 	else{
					// 		pattern_instance_pair.put(pattern_q, q);
					// 		// patter_length_map.put(pattern_q, triples.size());
					// 	}
					// }
				} catch (Exception e) {
					continue br1;
				}
			}
			else{
				System.out.println("Query is not any type of SELECT or CONSTRUCT or ASK or DESCRIBE:");
				System.out.println(q.serialize());
				continue;
			}
			
			int length = triples.size(); // get the length of the query
			if (instance_numbers.containsKey(length)) { // if the length is in the map
				instance_numbers.put(length, instance_numbers.get(length) + instance_freq.get(q)); // add the frequency of the query
			} else {
				instance_numbers.put(length, instance_freq.get(q)); // add the length and its frequency to the map
			}
		}
		Map<String, Long> couterMap = sortByValue(type_counting_list.stream().collect(Collectors.groupingBy(e -> e.toString(),Collectors.counting()))); // sort the map
		System.out.println("Statistics of number of query for each type:"+couterMap); // print the map
		try {
			BufferedWriter bw_type = new BufferedWriter(new FileWriter("rdftype_statistics.csv",true)); // write the map to file
			for(Map.Entry<String, Long> type_item : couterMap.entrySet()){ // for each type
				Query typequery = query_type.get(type_item.getKey()); // get the query of the type
				if(checkEndpoint){ // if check valid
					if (!StoreOrRead(typequery, dict_query, sparqlendpoint, dict_name)) // if the query is not valid
						continue;
				}
				bw_type.write(type_item.getKey().replace("\n", "\\n")+" & "+type_item.getValue().toString()+" & "+typequery.serialize().replace("\n", "\\n"));
				bw_type.newLine();
				bw_type.flush();
			}
			bw_type.close();
		} catch (Exception e) {
			// TODO: handle exception
		}


		// following code: function 2 results 
		Collections.shuffle(entity_rank_list);
		try {
			BufferedWriter bw_function2 = new BufferedWriter(new FileWriter("function2_results.txt", true));
			BufferedWriter bw_func_statistics = new BufferedWriter(new FileWriter("function2_statistiscs.txt",true)); // the file to store the random 50 queries of function 4
			List<Double> func2scores = new ArrayList<Double>();
			Set<Integer> complexities = new HashSet<Integer>();
			br_f2: for (String item : entity_rank_list) {
				if (func2scores.size() >= 50)
					break br_f2;
				if (iri_query.containsKey(item)) {
					for (Query query : iri_query.get(item).elementSet()) {
						if (func2scores.size() >= 50)
							break br_f2;
						if (StoreOrRead(query, dict_query, sparqlendpoint, dict_name)) {
							complexities.add(getBGPtripleNumber(query));
							double score = informativeness(query);
							func2scores.add(score);
							bw_function2
									.write(query.serialize().replace("\r", "\\r").replace("\n", "\\n"));
							bw_function2.newLine();
							bw_function2.flush();
							continue br_f2;
						}
					}
				}
			}
			double[] scores = new double[func2scores.size()];
			for (int i = 0; i < func2scores.size(); i++)
				scores[i] = func2scores.get(i);
			for(double s :ZScore(scores)){
				sum_info_func2+=s;
			}
			if (func2scores.size() != 50)
				System.err.println("The number of function2 outputs is not 50!!!");
				bw_func_statistics.write("Informativeness list:");
				bw_func_statistics.newLine();
				bw_func_statistics.write(func2scores.toString());
				bw_func_statistics.newLine();
				bw_func_statistics.write("Sum of informativeness:"+ sum_info_func2);
				bw_func_statistics.newLine();
				bw_func_statistics.write("Normalized Informativeness list:");
				bw_func_statistics.newLine();
				bw_func_statistics.write(Arrays.toString(ZScore(scores)) + scores.length);
				bw_func_statistics.newLine();
				bw_func_statistics.write("Complexity count:");
				bw_func_statistics.newLine();
				bw_func_statistics.write(Integer.toString(complexities.size()));
			cc2 = complexities.size();
			bw_function2.flush();
			bw_function2.close();
			bw_func_statistics.close();
		} catch (Exception e) {
			// TODO: handle exception
			System.err.println("Error during Function 2!!!");
		}


		// following code: top queries based on type
		try {
			BufferedWriter bw_type_top = new BufferedWriter(new FileWriter("type_top_query.txt", true)); 
			bw_type_top.write("subject");
			bw_type_top.newLine();
			bw_type_top.flush();
			for (String item : stop_list) {
				if(iri_query.containsKey(item))
				for (Query query : iri_query.get(item).elementSet()) {
					if (StoreOrRead(query, dict_query, sparqlendpoint, dict_name)){
						bw_type_top.write(item + " & " + query.serialize().replace("\r", "\\r").replace("\n", "\\n"));
						bw_type_top.newLine();
						bw_type_top.flush();
					}
				}
			}

			bw_type_top.write("predicate");
			bw_type_top.newLine();
			bw_type_top.flush();
			for (String item : ptop_List) {
				if(iri_query.containsKey(item))
				for (Query query : iri_query.get(item).elementSet()) {
					if (StoreOrRead(query, dict_query, sparqlendpoint, dict_name)){
						bw_type_top.write(item + " & " + query.serialize().replace("\r", "\\r").replace("\n", "\\n"));
						bw_type_top.newLine();
						bw_type_top.flush();
					}
				}
			}

			bw_type_top.write("object");
			bw_type_top.newLine();
			bw_type_top.flush();
			for (String item : otop_list) {
				if(iri_query.containsKey(item))
				for (Query query : iri_query.get(item).elementSet()) {
					if (StoreOrRead(query, dict_query, sparqlendpoint, dict_name)){
						bw_type_top.write(item + " & " + query.serialize().replace("\r", "\\r").replace("\n", "\\n"));
						bw_type_top.newLine();
						bw_type_top.flush();
					}
				}
			}

			bw_type_top.write("type");
			bw_type_top.newLine();
			bw_type_top.flush();
			for (String item : typetop_list) {
				if(iri_query.containsKey(item))
				for (Query query : iri_query.get(item).elementSet()) {
					if (StoreOrRead(query, dict_query, sparqlendpoint, dict_name)){
						bw_type_top.write(item + " & " + query.serialize().replace("\r", "\\r").replace("\n", "\\n"));
						bw_type_top.newLine();
						bw_type_top.flush();
					}
				}
			}
			bw_type_top.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			System.exit(1);
		}

		// System.out.println("Statistics of number of pattern in each length:"+pattern_numbers);
		System.out.println("Statistics of number of instance in each length:"+instance_numbers);
		List<Map.Entry<Query, Integer>> result = sortPatternByValue(findFrequentPattern(invalid_pattern_query)); // sort the map


		// following code: write the pattern and its frequency to file
		try {
			BufferedWriter bw2 = new BufferedWriter(new FileWriter("allPattern_frequency.csv",true));
			for(Entry<Query,Integer> uqf:result){
				bw2.write(uqf.getKey().serialize().replace("\r", "\\r").replace("\n", "\\n")+" & "+Integer.toString(uqf.getValue()));
				bw2.newLine();
				bw2.flush();
			}
			bw2.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
		

		// following code: write the pattern and its statistics to file
		try {
			BufferedWriter bw_pattern_instance = new BufferedWriter(new FileWriter("pattern_statistics.csv", true));
			for (Query pattern_q : pattern_instance.keySet()) {
				int count=0;
				for(DefaultEdge edge : graph.edgesOf(pattern_q)){
					count += instance_freq.get(graph.getEdgeTarget(edge));
				}
				bw_pattern_instance.write(pattern_q.serialize().replace("\r", "\\r").replace("\n", "\\n")+" & "+Integer.toString(graph.edgesOf(pattern_q).size())+" & "+Integer.toString(count));
				bw_pattern_instance.newLine();
				bw_pattern_instance.flush();
			}
			bw_pattern_instance.close();
		} catch (Exception e) {
			//TODO: handle exception
		}
		
		// following code: write the pattern and all instance to file
		try {
			BufferedWriter bw1 = new BufferedWriter(new FileWriter("patter_allQuery.csv",true));
			for(Entry<Query, HashMultiset<Query>> uqf:pattern_instance.entrySet()){
				bw1.write(uqf.getKey().serialize().replace("\r", "\\r").replace("\n", "\\n")+" & "+uqf.getValue().toString().replace("\r", "\\r").replace("\n", "\\n"));
				bw1.newLine();
				bw1.flush();
			}
			bw1.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
		for (Entry<Query, Integer> res : result) {
			int length = patter_length_map.get(res.getKey());
			if (pattern_numbers.containsKey(length)) {
				pattern_numbers.put(length, pattern_numbers.get(length) + 1);
			} else {
				pattern_numbers.put(length, 1);
			}
			// Algebra.compile(res.getKey()).visit(get_pattern_visitor);
		}
		System.out.println("Statistics of number of pattern in each length:"+pattern_numbers);
		List<Integer> triple_number_list = new ArrayList<Integer>(pattern_numbers.keySet());
		Collections.sort(triple_number_list);
        Collections.reverse(triple_number_list);
		tripleNumber = triple_number_list.get(0);
		Map<Integer,Integer> count_map = new HashMap<Integer,Integer>();
		for (int i =1;i<=tripleNumber;i++){
			count_map.put(i, 0);
		}
		result = sortPatternByValue(findFrequentPattern_checkQueryEquavalence(pattern_instance,instance_freq, sparqlendpoint));
		Map<Integer, Integer> unique_pattern_numbers = new HashMap<Integer, Integer>();
		for (Entry<Query, Integer> res : result) {
			int length = patter_length_map.get(res.getKey());
			if (unique_pattern_numbers.containsKey(length)) {
				unique_pattern_numbers.put(length, unique_pattern_numbers.get(length) + 1);
			} else {
				unique_pattern_numbers.put(length, 1);
			}
			// Algebra.compile(res.getKey()).visit(get_pattern_visitor);
		}
		System.out.println("Statistics of number of unique pattern in each length:"+unique_pattern_numbers);

		// for (int qi = 0; qi < result.size(); qi ++) {
		// 	Query qq = result.get(qi).getKey();
		// 	int n = getBGPtripleNumber(qq);
		// 	if (complex.contains(n))
		// 		continue;
		// 	if (StoreOrRead(qq, dict_query, sparqlendpoint, dict_name))
		// 		complex.add(getBGPtripleNumber(qq));
		// }


		// following code: function 3 results
		// 1. select 1 query per length
		// 2. if size < 50, continue find other queries from valid patterns
		// 3. if size still < 50, continue find random valid query
		try {
			BufferedWriter bw_func3 = new BufferedWriter(new FileWriter("function3.txt",true));
			BufferedWriter bw_func_statistics = new BufferedWriter(new FileWriter("function3_statistiscs.txt",true)); // the file to store the random 50 queries of function 4
			List<Double> func3scores = new ArrayList<Double>();// list to store all informativeness
			Set<Integer> complexities = new HashSet<Integer>();// set to store all length

			ProgressBar pb = new ProgressBar("Progress of function 3: ", 50);
			List<String> considered = new ArrayList<String>();

			// List<Double> func3scores_candidate = new ArrayList<Double>();
			// Set<Integer> complexities_candidate = new HashSet<Integer>();
			// List<Query> candidate_query = new ArrayList<Query>();
			HashMap<Query, Double> candidates = new HashMap<Query, Double>(); // hashmap to store all queries whose length already considered in outputs
			br2: for (int i = 0; !(check_count_all(count_map,top,offset,tripleNumber)) && i < result.size();i++){
				Query pattern_query = result.get(i).getKey(); // pattern query
				int num = getBGPtripleNumber(pattern_query); // length of pattern query
				
				
				
				if (num < offset || num > tripleNumber) // continue if length < 0 or > max which we set
					continue br2;
				if (check_count(count_map, num, top)) { // check if we already consider enough queries in current length
					continue br2;
				}

				// check if valid
				if (checkEndpoint)
					if (!pattern_query.isSelectType())
						if (!StoreOrRead(pattern_query,dict_query, sparqlendpoint, dict_name))
							continue br2;
				if (checkEndpoint)
					if (!StoreOrRead(pattern_query,dict_query, sparqlendpoint, dict_name)) {
						continue br2;
					}

				// find one valid instance
				Query query = null;
				br3: for (Query q : pattern_instance.get(pattern_query)) {
					if (checkEndpoint)
						if (StoreOrRead(q, dict_query, sparqlendpoint, dict_name)) {
							query = q;
							break br3;
						}
				}
				if(query == null)
					continue br2;
				
				// write to files, one file per length
				BufferedWriter bw = null;
				BufferedWriter bw_all = null;
				try {
					bw = new BufferedWriter(new FileWriter(
							"top" + Integer.toString(top) + "_pattern" + "_length" + Integer.toString(num) + ".json",
							true));
					bw_all = new BufferedWriter(
							new FileWriter("top" + Integer.toString(top) + "_pattern_with_frequency" + "_length"
									+ Integer.toString(num) + ".json", true));
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				JsonObject jo = new JsonObject();
				jo.put("Title", "");
				// jo.put("Pattern Rank Number",
				// Integer.toString(count+1)+"("+Integer.toString(i+1)+")");
				jo.put("SPARQL Query Pattern", pattern_query.serialize());
				jo.put("Instance Query", query.serialize());
				jo.put("Contained Triple's Number", num);

				// if current length does not have one query as output in function 3, we consider it
				if (!complexities.contains(num)){
					double score = informativeness(pattern_query); // get informativeness of current query
					func3scores.add(score);
					bw_func3.write(query.serialize().replace("\r", "\\r").replace("\n", "\\n"));
					bw_func3.newLine(); // write a new line
					bw_func3.flush(); // flush the buffer
					complexities.add(num);
					if (!complex.contains(num))
						complex.add(num);
					considered.add(query.serialize());
					pb.step();
				}
				else {
					double score = informativeness(pattern_query);
					candidates.put(query, score);
				}

				try {
					bw.write(jo.toString());
					bw.newLine();
					bw.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.exit(1);
				}


				// get frequency of current pattern and write to file
				int freq = 0;
				for (DefaultEdge edge : pattern_query_graph.edgesOf(pattern_query)){
					freq += instance_freq.get(pattern_query_graph.getEdgeTarget(edge));
				}
				jo.put("Frequency", freq);
				try {
					bw_all.write(jo.toString());
					bw_all.newLine();
					bw_all.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.exit(1);
				}
				count_map.put(num, count_map.get(num) + 1);
			}

			// if function 3 outputs do not contains 50 query, add queries from candidates
			for(Entry<Query, Double> qd : candidates.entrySet()){
				if(func3scores.size()<50){
					func3scores.add(qd.getValue());
					bw_func3.write(qd.getKey().serialize().replace("\r", "\\r").replace("\n", "\\n"));
					bw_func3.newLine(); // write a new line
					bw_func3.flush(); // flush the buffer
					considered.add(qd.getKey().serialize());
					pb.step();
				}
			}

			// if still not enough (50 queries), random add valid queries
			Collections.shuffle(valid_unique_query);
			while(func3scores.size()<50){
				for(Query uqf:valid_unique_query){ // for each unique query
					if(considered.contains(uqf.serialize()))
						continue;
					if(func3scores.size()>=50) // if the number of random queries is more than 50, then stop
						break;
					if (StoreOrRead(uqf, dict_query, sparqlendpoint, dict_name)){ // if the query is valid
						complexities.add(getBGPtripleNumber(uqf)); // add the complexity to the set
						double score = informativeness(uqf); // get the informativeness of the query
						func3scores.add(score); // add the informativeness to the list
						bw_func3.write(uqf.serialize().replace("\r", "\\r").replace("\n", "\\n")); // write the query to the file
						bw_func3.newLine(); // write a new line
						bw_func3.flush(); // flush the buffer
						pb.step();
					}
				}
			}
			pb.close();

			double[] scores = new double[func3scores.size()];
			for (int i = 0; i < func3scores.size(); i++)
				scores[i] = func3scores.get(i);
			for(double s :ZScore(scores)){
				sum_info_func3+=s;
			}
			if (func3scores.size() != 50)
				System.err.println("The number of function3 outputs is not 50!!!");
				bw_func_statistics.write("Informativeness list:");
				bw_func_statistics.newLine();
				bw_func_statistics.write(func3scores.toString());
				bw_func_statistics.newLine();
			bw_func_statistics.write("Sum of informativeness:"+ sum_info_func3);
			bw_func_statistics.newLine();
			bw_func_statistics.write("Normalized Informativeness list:");
			bw_func_statistics.newLine();
			bw_func_statistics.write(Arrays.toString(ZScore(scores)) + scores.length);
			bw_func_statistics.newLine();
			bw_func_statistics.write("Complexity count:");
			bw_func_statistics.newLine();
			bw_func_statistics.write(Integer.toString(complexities.size()));
			cc3 = complexities.size();
			bw_func3.flush();
			bw_func3.close();
			bw_func_statistics.close();
		} catch (Exception e) {
			// TODO: handle exception
			System.err.println("Error in function 3!");
		}
		System.out.print("Statistics of each length: ");
		System.out.println(count_map);
		// storeDict(dict_query);


		// following code: evaluation results
		try {
			cc1 = cc1 / complex.size();
			cc2 = cc2 / complex.size();
			cc3 = cc3 / complex.size();
			cc4 = cc4 / complex.size();

			double[] zarray = new double[]{sum_info_func1,sum_info_func2,sum_info_func3,sum_info_func4};

			zarray = ZScore(zarray);

			double u1 = cc1 + zarray[0];
			double u2 = cc2 + zarray[1];
			double u3 = cc3 + zarray[2];
			double u4 = cc4 + zarray[3];

			BufferedWriter bw_final_results = new BufferedWriter(new FileWriter("evaluation_results.txt",true));

			bw_final_results.write("Function 1:");
			bw_final_results.newLine();
			bw_final_results.write("Normalized Informativeness sum: "+zarray[0]);
			bw_final_results.newLine();
			bw_final_results.write("complexity coverage: "+cc1);
			bw_final_results.newLine();
			bw_final_results.write("Usefullness: "+u1);
			bw_final_results.newLine();

			bw_final_results.write("Function 2:");
			bw_final_results.newLine();
			bw_final_results.write("Normalized Informativenss sum: "+zarray[1]);
			bw_final_results.newLine();
			bw_final_results.write("complexity coverage: "+cc2);
			bw_final_results.newLine();
			bw_final_results.write("usefullness: "+u2);
			bw_final_results.newLine();

			bw_final_results.write("Function 3");
			bw_final_results.newLine();
			bw_final_results.write("Normalized Informativenss sum: "+zarray[2]);
			bw_final_results.newLine();
			bw_final_results.write("complexity coverage: "+cc3);
			bw_final_results.newLine();
			bw_final_results.write("usefullness: "+u3);
			bw_final_results.newLine();

			bw_final_results.write("Function 4");
			bw_final_results.newLine();
			bw_final_results.write("Normalized Informativenss sum: "+zarray[3]);
			bw_final_results.newLine();
			bw_final_results.write("complexity coverage: "+cc4);
			bw_final_results.newLine();
			bw_final_results.write("usefullness: "+u4);
			bw_final_results.newLine();
			bw_final_results.flush();

			bw_final_results.close();

		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	private static boolean check_count(Map<Integer,Integer> count_map,int num, int top){ // check if the number of pattern with length num is larger than top
		if (count_map.get(num) >= top) {
			return true;
		}
		return false;
	}

	private static boolean check_count_all(Map<Integer,Integer> count_map, int top, int offset, int tripleNumber){ // 
		for (int i =offset;i<=tripleNumber;i++){
			if(count_map.get(i)<top){
				return false;
			}
		}
		return true;
	}

	private static int getBGPtripleNumber(Query q){ // get the number of triples in the query
		List<Integer> num = new ArrayList<Integer>();
		AllBGPOpVisitor visitor = new AllBGPOpVisitor() {
			@Override
			public void visit(OpBGP opBGP) {
				for (Triple tp : opBGP.getPattern())
					num.add(1);
			}
		};
		try {
			Algebra.compile(q).visit(visitor);
		} catch (Exception e) {
			//TODO: handle exception
			return 0;
		}
		
		return num.size();
	}

	private static HashMap<Query, Integer> findFrequentQuery(List<Query> inputArr) { // sort list of queries by frequency
		HashMap<Query, Integer> numberMap = new HashMap<Query, Integer>();
		int frequency = -1;

		int value;
		for (int i = 0; i < inputArr.size(); i++) {

			value = -1;
			if (numberMap.containsKey(inputArr.get(i)))
				// if (numberMap.keySet().contains(inputArr.get(i))) {
				value = numberMap.get(inputArr.get(i));
				// }
			if (value != -1) {

				value += 1;
				if (value > frequency) {

					frequency = value;
				}

				numberMap.put(inputArr.get(i), value);
			} else {

				numberMap.put(inputArr.get(i), 1);
			}

		}
		return numberMap;
	}

	private static HashMap<Query, Integer> findFrequentPattern(List<Query> inputArr) { // sort list of pattern by frequency
		HashMap<Query, Integer> numberMap = new HashMap<Query, Integer>();
		int frequency = -1;

		int value;
		for (int i = 0; i < inputArr.size(); i++) {

			value = -1;
			if (numberMap.containsKey(inputArr.get(i)))
				if (listOflistContains(inputArr.get(i), numberMap.keySet())) {
					value = numberMap.get(inputArr.get(i));
				}
			if (value != -1) {

				value += 1;
				if (value > frequency) {

					frequency = value;
				}

				numberMap.put(inputArr.get(i), value);
			} else {

				numberMap.put(inputArr.get(i), 1);
			}

		}
		return numberMap;
	}

	private static double[] ZScore(double[] scores){ // z-score normalization
		int length = scores.length;
        double mean;
        double std_dev;

        // Calculate mean
        double sum = 0.0;
        for (double score : scores) {
            sum += score;
        }
        mean = sum / length;

        // Calculate standard deviation
        double temp = 0;
        for (double score : scores) {
            temp += (score - mean) * (score - mean);
        }
        std_dev = Math.sqrt(temp / length);

        // Normalize scores
        double[] normalizedScores = new double[length];
        for (int i = 0; i < length; i++) {
            normalizedScores[i] = (scores[i] - mean) / std_dev;
        }

        return normalizedScores;
	}

	private static boolean listOflistContains(Query list, Set<Query> listlist) { // check if the list of list contains the list
		if (listlist.contains(list))
			return true;
		List<Triple> list_pattern = new ArrayList<Triple>();
		AllOpVisitor list_visit = new AllOpVisitor() {
			@Override
			public void visit(OpBGP opBGP) {
				BasicPattern bp = simplify(opBGP.getPattern());
				list_pattern.addAll(bp.getList());
			}

			@Override
			public void visit(OpSlice opSlice) {
				// TODO Auto-generated method stub
				opSlice.getSubOp().visit(this);

			}
		};
		try{
		Algebra.compile(list).visit(list_visit);
		} catch (Exception e){
			return false;
		}
		for (Query temp : listlist) {
			List<Triple> temp_pattern = new ArrayList<Triple>();
			AllOpVisitor temp_visit = new AllOpVisitor() {
				@Override
				public void visit(OpBGP opBGP) {
					BasicPattern bp = simplify(opBGP.getPattern());
					temp_pattern.addAll(bp.getList());
				}

				@Override
				public void visit(OpSlice opSlice) {
					// TODO Auto-generated method stub
					opSlice.getSubOp().visit(this);

				}
			};
			try{
			Algebra.compile(temp).visit(temp_visit);
			} catch(Exception e){
				return false;
			}
			if (temp_pattern.containsAll(list_pattern) && list_pattern.containsAll(temp_pattern) && temp_pattern.size() == list_pattern.size()) {
				return true;
			}
		}
		return false;
	}

	private static HashMap<Query, Integer> findFrequentPattern_checkQueryEquavalence(HashMap<Query, HashMultiset<Query>> pattern_instance,HashMap<Query, Integer> instance_freq, String endpoint) { // sort list of pattern by frequency
		List<Query> inputArr = new ArrayList<Query>(pattern_instance.keySet());
		HashMap<Query, Integer> numberMap = new HashMap<Query, Integer>();
		int frequency = -1;

		int value;
		for (int i = 0; i < inputArr.size(); i++) {

			value = -1;
			if (numberMap.containsKey(inputArr.get(i)))
				// if (listOflistContains_checkQueryEquavalence(inputArr.get(i), numberMap.keySet(), endpoint,pattern_instance)) {
					value = numberMap.get(inputArr.get(i));
				// }
			if (value != -1) {

				value += 1;
				if (value > frequency) {

					frequency = value;
				}

				numberMap.put(inputArr.get(i), value);
			} else {
				int freq = 0;
				for(Query uni_q : pattern_instance.get(inputArr.get(i))){
					freq+= instance_freq.get(uni_q);
				}
				numberMap.put(inputArr.get(i), freq);
			}

		}
		return numberMap;
	}

	private static boolean listOflistContains_checkQueryEquavalence(Query list, Set<Query> listlist, String endpoint, HashMap<Query, HashMultiset<Query>> pattern_instance) {
		if (listlist.contains(list))
			return true;
		List<Triple> list_pattern = new ArrayList<Triple>();
		AllOpVisitor list_visit = new AllOpVisitor() {
			@Override
			public void visit(OpBGP opBGP) {
				BasicPattern bp = simplify(opBGP.getPattern());
				list_pattern.addAll(bp.getList());
			}

			@Override
			public void visit(OpSlice opSlice) {
				// TODO Auto-generated method stub
				opSlice.getSubOp().visit(this);

			}
		};
		try{
		Algebra.compile(list).visit(list_visit);
		} catch (Exception e){
			return false;
		}
		for (Query temp : listlist) {
			List<Triple> temp_pattern = new ArrayList<Triple>();
			AllOpVisitor temp_visit = new AllOpVisitor() {
				@Override
				public void visit(OpBGP opBGP) {
					BasicPattern bp = simplify(opBGP.getPattern());
					temp_pattern.addAll(bp.getList());
				}

				@Override
				public void visit(OpSlice opSlice) {
					// TODO Auto-generated method stub
					opSlice.getSubOp().visit(this);

				}
			};
			try{
			Algebra.compile(temp).visit(temp_visit);
			} catch(Exception e){
				return false;
			}
			if (temp_pattern.containsAll(list_pattern) && list_pattern.containsAll(temp_pattern) && temp_pattern.size() == list_pattern.size()) {
				if(check_query_equavalence(list,temp,endpoint) == true){
					pattern_instance.get(temp).addAll(pattern_instance.get(list));
					return true;
				}
				else
					return false;
			}
		}
		return false;
	}

	private static boolean check_query_equavalence(Query q1, Query q2, String endpoint){ // check if two queries are equavalent
		boolean bl = true;
		List<String> result1 = get_query_top_results_and_result_count(q1, endpoint);
		List<String> result2 = get_query_top_results_and_result_count(q2, endpoint);
		if(result1.size() == result2.size()){
			for(int i =0;i<result1.size();i++){
				if(!result1.get(i).equals(result2.get(i))){
					bl = false;
				}
			}
		} else {
			bl = false;
		}
		return bl;
	}

	private static List<String> get_query_top_results_and_result_count(Query q, String endpoint){ // get the top 5 results of the query
		List<String> outputs = new ArrayList<String>();
		QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, q.serialize());
        ResultSet rs = null;
		try {
        	rs = qexec.execSelect();
			outputs.add(Integer.toString(rs.getRowNumber()));
			outputs.add(Integer.toString(rs.getResultVars().size()));
			int count = 0;
            if (rs.hasNext() && count < 5) {
				String result = "";
				QuerySolution rb = rs.nextSolution();
				Iterator<String> varIte = rb.varNames();
				while(varIte.hasNext()){
					String var = varIte.next();
					result+= var+" "+rb.get(var).toString()+",";
				}
				outputs.add(result);
				count++;
			}
        }catch (Exception e) {
        	
        }
		qexec.close();
		return outputs;
	}

	private static BasicPattern simplify(BasicPattern bgp) { // simplify the query
		Model model = ModelFactory.createDefaultModel();
		BasicPattern bp = new BasicPattern();
		for (Triple triple : bgp) {
			org.apache.jena.graph.Node subject;
			org.apache.jena.graph.Node object;

			if (triple.getSubject().isVariable()) {
				subject = model.createLiteral("variable").asNode();
			} else if (triple.getSubject().isBlank()) {
				subject = model.createLiteral(EquivalenceClasses.ENTITY_GROUP).asNode();
			} else if (triple.getSubject().isLiteral()) {
				subject = model.createLiteral("literal").asNode();
			} else {
				subject = model
						.createLiteral(EquivalenceClasses.getEquivalentOrDefault(
								triple.getSubject().getIndexingValue().toString(), EquivalenceClasses.ENTITY_GROUP))
						.asNode();
			}
			if (triple.getObject().isVariable()) {
				object = model.createLiteral("variable").asNode();
			} else if (triple.getObject().isBlank()) {
				object = model.createLiteral(EquivalenceClasses.ENTITY_GROUP).asNode();
			} else if (triple.getObject().isLiteral()) {
				object = model.createLiteral("literal").asNode();
			} else {
				object = model
						.createLiteral(EquivalenceClasses.getEquivalentOrDefault(
								triple.getSubject().getIndexingValue().toString(), EquivalenceClasses.ENTITY_GROUP))
						.asNode();
			}

			bp.add(new Triple(subject, triple.getPredicate(), object));
		}

		return bp;

	}

	public static List<Map.Entry<Query, Integer>> sortPatternByValue(HashMap<Query, Integer> hm) { // sort pattern by frequency
		List<Map.Entry<Query, Integer>> list = new LinkedList<Map.Entry<Query, Integer>>(hm.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<Query, Integer>>() {
			public int compare(Map.Entry<Query, Integer> o1, Map.Entry<Query, Integer> o2) {
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});
		HashMap<Query, Integer> temp = new LinkedHashMap<Query, Integer>();
		for (Map.Entry<Query, Integer> aa : list) {
			try {
				temp.put(construcQuery(aa.getKey()), aa.getValue());
			} catch (Exception e) {
				//TODO: handle exception
			}
		}
		return list;
	}

	public static HashMap<Query, Integer> sortInstanceByValue(HashMap<Query, Integer> hm) { // sort instance by frequency
		List<Map.Entry<Query, Integer>> list = new LinkedList<Map.Entry<Query, Integer>>(hm.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<Query, Integer>>() {
			public int compare(Map.Entry<Query, Integer> o1, Map.Entry<Query, Integer> o2) {
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});
		HashMap<Query, Integer> temp = new LinkedHashMap<Query, Integer>();
		for (Map.Entry<Query, Integer> aa : list) {
			try {
				temp.put(construcQuery(aa.getKey()), aa.getValue());
				// temp.put(aa.getKey(), aa.getValue());
			} catch (Exception e) {
				//TODO: handle exception
			}
		}
		return temp;
	}

	public static HashMap<String, Long> sortByValue(Map<String, Long> hm) { // sort map by value
		List<Map.Entry<String, Long>> list = new LinkedList<Map.Entry<String, Long>>(hm.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<String, Long>>() {
			public int compare(Map.Entry<String, Long> o1, Map.Entry<String, Long> o2) {
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});
		HashMap<String, Long> temp = new LinkedHashMap<String, Long>();
		for (Map.Entry<String, Long> aa : list) {
			try {
				temp.put(aa.getKey(), aa.getValue());
				// temp.put(aa.getKey(), aa.getValue());
			} catch (Exception e) {
				//TODO: handle exception
			}
		}
		return temp;
	}

	private static boolean check_with_endpoint(Query query, String sparqlendpoint) { // check if query will return results via bio2rdf SPARQL endpoint
		boolean check = false;
		if(!query.isSelectType()){
			return check_ASK_CONSTRUCT_DESCRIBE(query, sparqlendpoint);
		}
		SelectBuilder selectBuilder = new SelectBuilder();
        HandlerBlock handlerBlock = new HandlerBlock(query);
        selectBuilder.getHandlerBlock().addAll(handlerBlock);
		selectBuilder.setLimit(1);
		selectBuilder.setBase(null);
        QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlendpoint, selectBuilder.build());
        ResultSet results = null;
        try {
        	results = qexec.execSelect();
        }catch (Exception e) {
			qexec.close();
        	return false;
        }
        if (results.hasNext()) {
        	check = true;
        }
		qexec.close();
        return check;
	}

	private static boolean check_ASK_CONSTRUCT_DESCRIBE(Query query, String sparqlendpint){ // check if query will return results via bio2rdf SPARQL endpoint
		boolean check = false;
		if (query.isAskType()) {
			AskBuilder selectBuilder = new AskBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			selectBuilder.getHandlerBlock().addAll(handlerBlock);
			selectBuilder.setLimit(1);
			selectBuilder.setBase(null);
			// selectBuilder.addVar("*");
			QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlendpint,
					selectBuilder.build());
			ResultSet results = null;
			try {
				check = qexec.execAsk();
			} catch (Exception e) {
			}
		} else if (query.isConstructType()) {
			ConstructBuilder selectBuilder = new ConstructBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			selectBuilder.getHandlerBlock().addAll(handlerBlock);
			selectBuilder.setLimit(1);
			selectBuilder.setBase(null);
			// selectBuilder.addVar("*");
			QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlendpint,
					selectBuilder.build());
			Model results = null;
			try {
				results = qexec.execConstruct();
			} catch (Exception e) {
			}
			if (results == null){
				qexec.close();
				return false;
			}
			if (results.isEmpty()){
				qexec.close();
				return false;
			}
				
			check = true;
			qexec.close();
		} else if (query.isDescribeType()) {
			DescribeBuilder selectBuilder = new DescribeBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			selectBuilder.getHandlerBlock().addAll(handlerBlock);
			selectBuilder.setLimit(1);
			selectBuilder.setBase(null);
			// selectBuilder.addVar("*");
			QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlendpint,
					selectBuilder.build());
			Model results = null;
			try {
				results = qexec.execDescribe();
			} catch (Exception e) {
			}
			if (results == null){
				qexec.close();
				return false;
			}
			if (results.isEmpty()){
				qexec.close();
				return false;
			}
			check = true;
			qexec.close();
		}
		return check;
	}

	public static Map<Var,org.apache.jena.graph.Node> get_result_of_vars(Query query, String sparqlendpoint){ // get var-value pairs for pattern query via endpoint 
		Map<Var,org.apache.jena.graph.Node> var_results = new HashMap<Var,org.apache.jena.graph.Node>();
		if (query.isSelectType()){
			SelectBuilder selectBuilder = new SelectBuilder();
        	HandlerBlock handlerBlock = new HandlerBlock(query);
			selectBuilder.getHandlerBlock().addAll(handlerBlock);
			selectBuilder.setLimit(1);
			selectBuilder.setBase(null);
			// selectBuilder.addVar("*");
			QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlendpoint,
					selectBuilder.build());
			ResultSet results = null;
			try {
				results = qexec.execSelect();
			} catch (Exception e) {
				qexec.close();
				return var_results;
			}
			if (results.hasNext()) {
				QuerySolution qs = results.next();
				Iterator<String> it_var = qs.varNames();
				while (it_var.hasNext()) {
					String var = it_var.next();
					var_results.put(Var.alloc(var), new NodeFactory().createURI(qs.get(var).toString()));
				}
			}
			qexec.close();
		}
		
        return var_results;
	}
	
	private static Graph toGraph(Query pattern){ // convert query to graph
		Graph<Node, RelationshipEdge> graph = new DefaultDirectedGraph<Node, RelationshipEdge>(RelationshipEdge.class);
		AllBGPOpVisitor visitor = new AllBGPOpVisitor() {

			@Override
			public void visit(OpBGP opBGP) {
				for (Triple tp : opBGP.getPattern()){
					org.apache.jena.graph.Node s = tp.getSubject();
					org.apache.jena.graph.Node o = tp.getObject();
					org.apache.jena.graph.Node p = tp.getPredicate();

					RelationshipEdge edge = new RelationshipEdge(p.toString());
					Node sNode = null;
					if (s.isVariable() || s.toString().startsWith("?")){
						if(s.toString().startsWith("?variable")){
							sNode = new Node(s.toString().replace("?",""), "variable");
						}
						else if (s.toString().startsWith("?ent")){
							sNode = new Node(s.toString().replace("?", ""), "entity");
						}
						else if (s.toString().startsWith("?str")){
							sNode = new Node(s.toString().replace("?", ""), "literal");
						}
					}
					else {
						sNode = new Node(s.toString(), s.toString());
					}

					Node oNode = null;
					if (o.isVariable() || o.toString().startsWith("?")){
						if(o.toString().startsWith("?variable")){
							oNode = new Node(o.toString().replace("?",""), "variable");
						}
						else if (o.toString().startsWith("?ent")){
							oNode = new Node(o.toString().replace("?", ""), "entity");
						}
						else if (o.toString().startsWith("?str")){
							oNode = new Node(o.toString().replace("?", ""), "literal");
						}
					}
					else {
						oNode = new Node(o.toString(),o.toString());
					}
					graph.addVertex(sNode);
					graph.addVertex(oNode);
					graph.addEdge(sNode, oNode, edge);
				}
			}
		};

		Op op = Algebra.compile(pattern);
		op.visit(visitor);
		
		return graph;
	}

	private static boolean StoreOrRead(Query query,Map<Query, Boolean> dict_query, String sparqlendpoint, String dict_name){ // store or read query results
		if(dict_query.keySet().contains(query)){
			return dict_query.get(query);
		}
		boolean bl = check_with_endpoint(query, sparqlendpoint);
		dict_query.put(query, bl);
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(dict_name, true));
			bw.write(query.serialize().replace("\n", "\\n").replace("\r", "\\r") + " & " + Boolean.toString(bl));
			bw.newLine();
			bw.flush();
			bw.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return bl;
	}

	private static Map<Query, Boolean> getDict(String dict_name) { // read query results
		Map<Query, Boolean> dict_query = new HashMap<Query, Boolean>();
		if (!new File(dict_name).exists()) {
			return dict_query;
		} else {
			try {
				BufferedReader br = new BufferedReader(new FileReader(dict_name));
				String line = null;
				while ((line = br.readLine()) != null) {
					String[] splitline = line.split(" & ");
					try {
						dict_query.put(construcQuery(splitline[0].replace("\\n", "\n").replace("\\r", "\r")), Boolean.parseBoolean(splitline[1]));
					} catch (Exception e) {
						// TODO: handle exception
						// System.out.println(splitline[0]);
						// e.printStackTrace();
					}
				}
				br.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
			}
		}
		return dict_query;
	}

    private static Query construcQuery(String queryString){ // construct query
		Query query = QueryFactory.create(queryString);
		// Op op = Algebra.compile(query);
		// query = OpAsQuery.asQuery(op);
		if(query.isSelectType()){
			SelectBuilder builder = new SelectBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			query = builder.build();
		}
		else if(query.isAskType()){
			AskBuilder builder = new AskBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			query = builder.build();
		}
		else if (query.isConstructType()){
			ConstructBuilder builder = new ConstructBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			query = builder.build();
		}
		else if (query.isDescribeType()){
			DescribeBuilder builder = new DescribeBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			query = builder.build();
		}
		return query;
	}

	private static Query construcQuery(Query query){ // construct query
		// Query query = QueryFactory.create(queryString);
		// Op op = Algebra.compile(query);
		// query = OpAsQuery.asQuery(op);
		if(query.isSelectType()){
			SelectBuilder builder = new SelectBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			query = builder.build();
		}
		else if(query.isAskType()){
			AskBuilder builder = new AskBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			query = builder.build();
		}
		else if (query.isConstructType()){
			ConstructBuilder builder = new ConstructBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			query = builder.build();
		}
		else if (query.isDescribeType()){
			DescribeBuilder builder = new DescribeBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			query = builder.build();
		}
		return query;
	}

	private static Query construcQuery_removeLimitOffset(Query query){ // construct query remove limit and offset
		// Query query = QueryFactory.create(queryString);
		// Op op = Algebra.compile(query);
		// query = OpAsQuery.asQuery(op);
		if(query.isSelectType()){
			SelectBuilder builder = new SelectBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			builder.setLimit(0);
			builder.setOffset(0);
			query = builder.build();
		}
		else if(query.isAskType()){
			AskBuilder builder = new AskBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			builder.setLimit(0);
			builder.setOffset(0);
			query = builder.build();
		}
		else if (query.isConstructType()){
			ConstructBuilder builder = new ConstructBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			builder.setLimit(0);
			builder.setOffset(0);
			query = builder.build();
		}
		else if (query.isDescribeType()){
			DescribeBuilder builder = new DescribeBuilder();
			HandlerBlock handlerBlock = new HandlerBlock(query);
			builder.getHandlerBlock().addAll(handlerBlock);
			builder.setBase(null);
			builder.setLimit(0);
			builder.setOffset(0);
			query = builder.build();
		}
		return query;
	}

	private static Set<String> get_bind_vars(Query query){ // get bind vars
		HashSet<String> bindvars = new HashSet<String>();
		SerializationContext cxt = new SerializationContext();
        IndentedLineBuffer b = new IndentedLineBuffer();
        FormatterElement visitor = new FormatterElement(b, cxt){
            @Override
            public void visit(ElementBind el) {
                bindvars.add(el.getVar().getVarName());
            }
        };
		try {
			query.getQueryPattern().visit(visitor);
		} catch (Exception e) {
			//TODO: handle exception
		}
		return bindvars;
	}

	private static double informativeness(Query query){ // calculate informativeness
		List<Triple> triples = new ArrayList<Triple>();
		Op op = Algebra.compile(query);
		AllOpVisitor allbgp = new AllOpVisitor() {
			@Override
			public void visit(OpBGP opBGP) {
				for(Triple t: opBGP.getPattern().getList()){
					triples.add(t);
				}
			}

			@Override
			public void visit(OpSlice opSlice) {
				opSlice.getSubOp().visit(this);
			}

			public void visit(OpExtend opExtend){
				opExtend.getSubOp().visit(this);
			}

			@Override
			public void visit(OpGroup opGroup){
				opGroup.getSubOp().visit(this);
			}

			@Override
			public void visit(OpTable opTable){
			}
		};
		op.visit(allbgp);		
		return entity_vairable_score(triples);
	}

	private static double entity_vairable_score(List<Triple> opBGP){ // calculate entity variable score
		List<String> ent_set = new ArrayList<String>();
		List<String> var_set = new ArrayList<String>();
		for(Triple t: opBGP){
			org.apache.jena.graph.Node s = t.getSubject();
				if (s.isURI()) {
					ent_set.add(s.getURI());
				} else if (s.isVariable()) {
					// TODO
					var_set.add(s.toString());
				} else {
					// blank nodes ingored
				}
				org.apache.jena.graph.Node p = t.getPredicate();
				if (p.isURI()) {
					ent_set.add(p.getURI());
				} else if (p.isVariable()) {
					// TODO
					var_set.add(p.toString());
				} else {
					throw new AssertionError("This should never happen");
				}
				org.apache.jena.graph.Node o = t.getObject();
				if (o.isURI()) {
					ent_set.add(o.getURI());
				} else if (o.isVariable()) {
					// TODO
					var_set.add(o.toString());
				} else if (o.isLiteral()) {

				} else {
					// blank nodes ingored
				}
		}
		if(var_set.isEmpty())
			return (double)ent_set.size();
		
		return ((double) ent_set.size())/((double) var_set.size());
	}
}

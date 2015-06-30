package org.wikipathways.geneparcer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bridgedb.BridgeDb;
import org.bridgedb.DataSource;
import org.bridgedb.IDMapper;
import org.bridgedb.IDMapperException;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.bio.Organism;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSPathway;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

/**
 * 
 * @author Bram
 *
 */
public class PathwayGeneParser {

	private WikiPathwaysClient client;
	private IDMapper mapper;
	private File outputNodes;
	private File outputEdges;
	
	
	public PathwayGeneParser(String url, File bridgeDbFile, File outputNodes, File outputEdges) throws MalformedURLException, ClassNotFoundException, IDMapperException {
		client = new WikiPathwaysClient(new URL(url));
		
		// setting up bridgedb
		DataSourceTxt.init();
		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		mapper = BridgeDb.connect("idmapper-pgdb:" + bridgeDbFile.getAbsolutePath());
		
		this.outputEdges = outputEdges;
		this.outputNodes = outputNodes;
	}
	
	public void parsePathways() throws Exception {
		BufferedWriter writerE = new BufferedWriter(new FileWriter(outputEdges));
		BufferedWriter writerN = new BufferedWriter(new FileWriter(outputNodes));
		
		Map<String, String> map = new HashMap<String, String>();
		Map<String, String> pathwayset = new HashMap<String,String>();
		
		System.out.println("[INFO]\t Get all pathways.");
		WSPathwayInfo[] info = client.listPathways(Organism.HomoSapiens);
		System.out.println("[INFO]\t Number of pathways: " + info.length);

		System.out.println("[INFO]\t Filter curated collection.");
		// check if pathway is in curated collection
		List<WSPathwayInfo> pwyList = new ArrayList<WSPathwayInfo>();
		for (WSPathwayInfo i : info) {
			for (WSCurationTag tag : client.getCurationTags(i.getId())) {
				if (tag.getName().equals("Curation:AnalysisCollection")) {
					pwyList.add(i);
				}
			}
		}
		System.out.println("[INFO]\t Number of curated pathways: "
				+ pwyList.size());

		System.out.println("[INFO]\t Get all datanodes.");
		Map<String, HashSet<String>> edgemapper = new HashMap<String,HashSet<String>>(); 
		for (WSPathwayInfo i : pwyList) {
			WSPathway p = client.getPathway(i.getId());
			HashSet<String> edgemap = new HashSet<String>();

			// get general information
			String name = p.getName();
			String id = p.getId();
			if (!pathwayset.containsKey(id)){
				pathwayset.put(id, name);
			}
			Pathway pathway = WikiPathwaysClient.toPathway(p);

			for (PathwayElement element : pathway.getDataObjects()) {
				String nodeId = element.getElementID();
				String nodeName = element.getTextLabel().replace("\n", "");

				// make sure nodeId and nodeName are not empty
				if (nodeId != null && !nodeId.equals("") && nodeName != null
						&& !nodeName.equals("")) {

					// since we only want to provide genes and proteins
					// we need to filter the nodes based on data node type
					if (element.getDataNodeType().equals("GeneProduct")
							|| element.getDataNodeType().equals("Protein")) {

						// Mapping to Ensembl Id and writing in text file
						Set<Xref> result = mapper.mapID(element.getXref(), DataSource.getExistingBySystemCode("En"));
						for (Xref xref: result) {	
							if(xref.getId().startsWith("ENS")) {
								if (!map.containsKey(xref.getId()) ) {
	
									Set<Xref> hgncResult = mapper.mapID(xref, DataSource.getExistingBySystemCode("H"));
									if(hgncResult.size() > 0) {
										nodeName = hgncResult.iterator().next().getId();						
										map.put(xref.getId(), nodeName);
									}
	
								}
								edgemap.add(xref.getId());
								//edgemap.putIfAbsent(xref.getId(),id);
							}
						}
					}
				}
			}
			if(!edgemapper.containsKey(id)) edgemapper.put(id, edgemap);
		}

			// writing all genes by ensemble Id into the nodes.txt file
		writerE.write("Source\tTarget\n");
		for (String p : edgemapper.keySet()){
			for(String key:edgemapper.get(p)){
				writerE.write(p+"\t"+key+"\n");
			}
		}
		writerN.write("Identifier\tName\tType\tCategory\n");
		for (String key : map.keySet()) {

			writerN.write(key	+ "\t" + map.get(key) + "\tGene\t0"+"\n");
		}
		
		for (String key: pathwayset.keySet()){

			writerN.write(key	+ "\t" + pathwayset.get(key) + "\tPathway\t0"+"\n");
		}
					
		 System.out.println("[INFO]\t All pathways done.");
		 writerN.close();
		 writerE.close();	
	}
	
	public static void main(String[] args) throws Exception {
		PathwayGeneParser parser = new PathwayGeneParser("http://webservice.wikipathways.org", 
				new File("C:\\Users\\martina.kutmon\\Workspace\\bram\\Hs_Derby_Ensembl_79_v.01.bridge"), 
				new File("nodes.txt"), 
				new File("edges.txt"));
		
		parser.parsePathways();
	}
}

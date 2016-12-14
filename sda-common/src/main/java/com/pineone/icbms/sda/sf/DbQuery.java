package com.pineone.icbms.sda.sf;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.pineone.icbms.sda.comm.util.Utils;
/*
 * db에 접속하여 쿼리수행
 */
public class DbQuery extends QueryCommon implements QueryItf {

	private final Log log = LogFactory.getLog(this.getClass());
	
	@Override
	public List<Map<String, String>> runQuery(String query, String[] idxVals) throws Exception {
		List<Map<String, String>> list = new ArrayList<Map<String, String>>();

		log.info("runQuery start ======================>");

		log.debug("try (first) .................................. ");
		try {
			list = getResult(query, idxVals);
		} catch (Exception e) {
			int waitTime = 5*1000;
			log.debug("Exception message in runQuery() =====> "+e.getMessage());  
			
			try {
				// 일정시간 대기 했다가 다시 수행함
				log.debug("sleeping (first)................................. in "+waitTime);
				Thread.sleep(waitTime);
				
				log.debug("try (second).................................. ");
				list = getResult(query, idxVals);
			} catch (Exception ee) {
				log.debug("Exception 1====>"+ee.getMessage());
				if(ee.getMessage().contains("Service Unavailable")|| ee.getMessage().contains("java.net.ConnectException")
						// || ee.getMessage().contains("500 - Server Error") || ee.getMessage().contains("HTTP 500 error")
						) {					
					try {
						// restart fuseki
						Utils.restartFuseki();
					
						// 일정시간을 대기 한다.
						log.debug("sleeping (final)................................. in "+waitTime);
						Thread.sleep(waitTime);
						
						// 마지막으로 다시한번 처리해줌
						log.debug("try (final).................................. ");
						list = getResult(query, idxVals);
					} catch (Exception eee) {
						log.debug("Exception 2====>"+eee.getMessage());
						throw eee;
					}
				}
				throw ee;
			}
		}

		log.info("runQuery end ======================>");
		return list;
	}
	
	private final List<Map<String, String>> getResult (String query, String[] idxVals) throws Exception {
		String db_server = Utils.getSdaProperty("com.pineone.icbms.sda.stat.db.server");
		String db_port = Utils.getSdaProperty("com.pineone.icbms.sda.stat.db.port");
		String db_name = Utils.getSdaProperty("com.pineone.icbms.sda.stat.db.name");
		String db_user = Utils.getSdaProperty("com.pineone.icbms.sda.stat.db.user");
		String db_pass = Utils.getSdaProperty("com.pineone.icbms.sda.stat.db.pass");

		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		// 변수치환
		query = makeFinal(query, idxVals);
	
		List<Map<String, String>> list = new ArrayList<Map<String, String>>();
		
		try {
			Class.forName("org.mariadb.jdbc.Driver");
			conn = DriverManager.getConnection("jdbc:mariadb://" + db_server + ":" + db_port + "/" + db_name, db_user,  db_pass);
			
			pstmt = conn.prepareStatement(query);
			int k =  pstmt.executeUpdate("set @rownum:=0;");
			rs = pstmt.executeQuery();

			//rs.setFetchDirection(ResultSet.FETCH_FORWARD);
			int cnt = 0;
	
			ResultSetMetaData md = rs.getMetaData();
			int columns = md.getColumnCount();
			while (rs.next()){
			   HashMap<String,String> row = new HashMap<String, String>(columns);
			   for(int i=1; i<=columns; i++){           
				   row.put(md.getColumnName(i), rs.getObject(i).toString());
			   }
			   log.debug("row("+(cnt++)+")  ===========>"+row.toString());
			    list.add(row);
			}
			
			// 정렬
	        // Collections.sort(list, new MapStringComparator("rest_type"));
	        //Collections.sort(list, new MapStringComparator("corner_id"));
	        //Collections.sort(list, new MapFloatComparator("cnt"));
	        
			return list;
		} catch (Exception e) {
			throw e;
		} finally {
			if (rs != null)
				try {
					rs.close();
				} catch (SQLException sqle) {
				}
			if (pstmt != null)
				try {
					pstmt.close();
				} catch (SQLException sqle) {
				}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException sqle) {
				}
			}
		}
	}
	
	class MapFloatComparator implements Comparator<Map<String, String>> {
		private final String key;
		
		public MapFloatComparator(String key) {
			this.key = key;
		}
		
		@Override
		public int compare(Map<String, String> first, Map<String, String> second) {
			float firstValue = Float.valueOf(first.get(key));
	         float secondValue = Float.valueOf(second.get(key));
	         
			// 내림차순 정렬
	         if (firstValue > secondValue) {
	             return -1;
	         } else if (firstValue < secondValue) {
	             return 1;
	         } else /* if (firstValue == secondValue) */ {
	             return 0;
	         }
		}
	}
	
	class MapStringComparator implements Comparator<Map<String, String>> {
		private final String key;
		
		public MapStringComparator(String key) {
			this.key = key;
		}
		
		@Override
		public int compare(Map<String, String> first, Map<String, String> second) {
			String firstValue =first.get(key);
	        String secondValue = second.get(key);
	        
	         // 내림차순 정렬
             return firstValue.compareTo(secondValue);
		}
	}

}
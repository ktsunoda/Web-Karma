/**
 * *****************************************************************************
 * Copyright 2012 University of Southern California
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * This code was developed by the Information Integration Group as part of the
 * Karma project at the Information Sciences Institute of the University of
 * Southern California. For more information, publications, and related
 * projects, please see: http://www.isi.edu/integration
 * ****************************************************************************
 */
package edu.isi.karma.imp.csv;

import au.com.bytecode.opencsv.CSVReader;
import edu.isi.karma.imp.Import;
import edu.isi.karma.rep.*;
import edu.isi.karma.rep.metadata.WorksheetProperties.Property;
import edu.isi.karma.rep.metadata.WorksheetProperties.SourceTypes;
import edu.isi.karma.util.EncodingDetector;
import edu.isi.karma.webserver.KarmaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CSVFileImport extends Import {

    private final int headerRowIndex;
    private final int dataStartRowIndex;
    private final char delimiter;
    private final char quoteCharacter;
    private final char escapeCharacter = '\\';
    private final File csvFile;
    private final String encoding;
    private final int maxNumLines;
    private static Logger logger = LoggerFactory.getLogger(CSVFileImport.class);

    public CSVFileImport(int headerRowIndex, int dataStartRowIndex,
            char delimiter, char quoteCharacter, String encoding,
            int maxNumLines,
            File csvFile,
            Workspace workspace) {

        super(csvFile.getName(), workspace, encoding);
        this.headerRowIndex = headerRowIndex;
        this.dataStartRowIndex = dataStartRowIndex;
        this.delimiter = delimiter;
        this.quoteCharacter = quoteCharacter;
        this.encoding = encoding;
        this.maxNumLines = maxNumLines;
        this.csvFile = csvFile;
    }

    @Override

    public Worksheet generateWorksheet() throws IOException, KarmaException {
        Table dataTable = getWorksheet().getDataTable();

        // Prepare the reader for reading file line by line
        
        InputStreamReader isr = EncodingDetector.getInputStreamReader(csvFile, encoding);
        
        BufferedReader br = new BufferedReader(isr);
        
        //Index for row currently being read
        int rowCount = 0;
        ArrayList<String> hNodeIdList = new ArrayList<String>();
        
        //If no row is present for the column headers
        if (headerRowIndex == 0) {
            hNodeIdList = addEmptyHeaders(getWorksheet(), getFactory());
            if (hNodeIdList == null || hNodeIdList.size() == 0) {
                br.close();
                throw new KarmaException("Error occured while counting header "
                        + "nodes for the worksheet!");
            }
        }
        
        CSVReader reader = new CSVReader(br, delimiter,
                quoteCharacter, escapeCharacter);
        String[] rowValues = reader.readNext();;
        
        while(rowValues != null && rowValues.length > 0) {
        	if (rowCount + 1 == headerRowIndex) {
                hNodeIdList = addHeaders(getWorksheet(), getFactory(), rowValues);
                rowCount++;
            } else if (rowCount + 1 >= dataStartRowIndex) {
                boolean added = addRow(getWorksheet(), getFactory(), rowValues, hNodeIdList, dataTable);
                if(added) {
	                rowCount++;
	               
	                if(maxNumLines > 0 && (rowCount - dataStartRowIndex) >= maxNumLines-1) {
	                	break;
	                }
                }
            }            
        	rowValues = reader.readNext();
        }

        reader.close();
        br.close();
        getWorksheet().getMetadataContainer().getWorksheetProperties().setPropertyValue(Property.sourceType, SourceTypes.CSV.toString());
        return getWorksheet();
    }

    private ArrayList<String> addHeaders(Worksheet worksheet, RepFactory fac,
            String[] rowValues) throws IOException {
        HTable headers = worksheet.getHeaders();
        ArrayList<String> headersList = new ArrayList<String>();
        
        if (rowValues == null || rowValues.length == 0) {
            return addEmptyHeaders(worksheet, fac);
        }

        for (int i = 0; i < rowValues.length; i++) {
            HNode hNode = null;
            if (headerRowIndex == 0) {
                hNode = headers.addHNode("Column_" + (i + 1), worksheet, fac);
            } else {
                hNode = headers.addHNode(rowValues[i], worksheet, fac);
            }
            headersList.add(hNode.getId());
        }
        return headersList;
    }

    private boolean addRow(Worksheet worksheet, RepFactory fac, String[] rowValues,
            List<String> hNodeIdList, Table dataTable) throws IOException {
       
        if (rowValues == null || rowValues.length == 0) {
            return false;
        }

        Row row = dataTable.addRow(fac);
        for (int i = 0; i < rowValues.length; i++) {
            if (i < hNodeIdList.size()) {
                row.setValue(hNodeIdList.get(i), rowValues[i], fac);
            } else {
                // TODO Our model does not allow a value to be added to a row
                // without its associated HNode. In CSVs, there could be case
                // where values in rows are greater than number of column names.
                logger.error("More data elements detected in the row than number of headers!");
            }
        }
        return true;
    }

    private ArrayList<String> addEmptyHeaders(Worksheet worksheet,
            RepFactory fac) throws IOException {
        HTable headers = worksheet.getHeaders();
        ArrayList<String> headersList = new ArrayList<String>();

        Scanner scanner = null;
        scanner = new Scanner(csvFile);

        // Use the first data row to count the number of columns we need to add
        int rowCount = 0;
        while (scanner.hasNext()) {
            if (rowCount + 1 == dataStartRowIndex) {
                String line = scanner.nextLine();
                CSVReader reader = new CSVReader(new StringReader(line),
                        delimiter, quoteCharacter, escapeCharacter);
                String[] rowValues = null;
                try {
                    rowValues = reader.readNext();
                } catch (IOException e) {
                    logger.error("Error reading Line:" + line, e);
                }
                for (int i = 0; i < rowValues.length; i++) {
                    HNode hNode = headers.addHNode("Column_" + (i + 1),
                            worksheet, fac);
                    headersList.add(hNode.getId());
                }
                reader.close();
                break;
            }
            rowCount++;
            if (scanner.hasNext()) {
                scanner.nextLine();
            }
        }
        scanner.close();
        return headersList;
    }

}

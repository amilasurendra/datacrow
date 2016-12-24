/******************************************************************************
 *                                     __                                     *
 *                              <-----/@@\----->                              *
 *                             <-< <  \\//  > >->                             *
 *                               <-<-\ __ /->->                               *
 *                               Data /  \ Crow                               *
 *                                   ^    ^                                   *
 *                              info@datacrow.net                             *
 *                                                                            *
 *                       This file is part of Data Crow.                      *
 *       Data Crow is free software; you can redistribute it and/or           *
 *        modify it under the terms of the GNU General Public                 *
 *       License as published by the Free Software Foundation; either         *
 *              version 3 of the License, or any later version.               *
 *                                                                            *
 *        Data Crow is distributed in the hope that it will be useful,        *
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.             *
 *           See the GNU General Public License for more details.             *
 *                                                                            *
 *        You should have received a copy of the GNU General Public           *
 *  License along with this program. If not, see http://www.gnu.org/licenses  *
 *                                                                            *
 ******************************************************************************/

package net.datacrow.core.migration.itemexport;

import net.datacrow.core.DcConfig;
import net.datacrow.core.DcThread;
import net.datacrow.core.console.UIComponents;
import net.datacrow.core.modules.DcModule;
import net.datacrow.core.modules.DcModules;
import net.datacrow.core.objects.*;
import net.datacrow.core.resources.DcResources;
import net.datacrow.core.server.Connector;
import org.apache.log4j.Logger;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumn;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumns;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;

/**
 * @author Amila Surendra
 */
public class ExcelExporter extends ItemExporter  {

    private static Logger logger = Logger.getLogger(ExcelExporter.class.getName());

    public ExcelExporter(int moduleIdx, int mode, boolean processChildren) throws Exception {

        super(moduleIdx, "EXCEL", mode, false);
    }

    @Override public DcThread getTask() {
        return new Task(items);
    }

    @Override public String getName() {
        return DcResources.getText("lblExcelExport");
    }

    @Override public String getFileType() {
        return "xlsx";
    }

    private class Task extends DcThread{

        private List<String> items;

        public Task(Collection<String> items) {
            super(null, "Excel export to " + file);
            this.items = new ArrayList<String>();
            this.items.addAll(items);
        }

        @Override
        public void run() {
            try {
                create();
            } catch (Exception exp) {
                success = false;
                logger.error(DcResources.getText("msgErrorWhileCreatingReport", exp.toString()), exp);
                client.notify(DcResources.getText("msgErrorWhileCreatingReport", exp.toString()));
            } finally {
                client.notifyTaskCompleted(true, null);
            }

            items.clear();
            items = null;
        }

        private void create() throws Exception {

            int rowCount = items.size() + 1;
            int colCount = getFields().length;

            if (items == null || items.size() == 0) return;

            Workbook wb = new XSSFWorkbook();
            XSSFSheet sheet = (XSSFSheet) wb.createSheet();

            //Create
            XSSFTable table = sheet.createTable();
            table.setDisplayName("Test");
            CTTable cttable = table.getCTTable();

            //Style configurations
            CTTableStyleInfo style = cttable.addNewTableStyleInfo();
            style.setName("TableStyleMedium2");
            style.setShowColumnStripes(false);
            style.setShowRowStripes(true);

            //Set which area the table should be placed in
            AreaReference reference = new AreaReference(new CellReference(0, 0),
                new CellReference(rowCount - 1,colCount - 1));
            cttable.setRef(reference.formatAsString());
            cttable.setId(1);
            cttable.setName("Test");

            String tableData[][] = getTableData();

            CTTableColumns columns = cttable.addNewTableColumns();
            columns.setCount(colCount);
            CTTableColumn column;
            XSSFRow row;
            XSSFCell cell;

            for (int columnIdx = 0; columnIdx < colCount; columnIdx++)
            {
                column = columns.addNewTableColumn();
                column.setName("Column" + columnIdx);
                column.setId(columnIdx+1);
            }

            for(int rowIdx = 0; rowIdx < rowCount ; rowIdx++) {
                row = sheet.createRow(rowIdx);
                for(int columnIdx=0 ; columnIdx < colCount ; columnIdx++) {
                    cell = row.createCell(columnIdx);
                    cell.setCellValue(tableData[rowIdx][columnIdx]);
                }
            }
            wb.write(bos);
            bos.flush();
            bos.close();
            client.notify(DcResources.getText("lblExportHasFinished"));
        }

        private String[][] getTableData(){

            int rowCount = items.size() + 1;
            int colCount = getFields().length;

            ItemExporterUtilities utilities = new ItemExporterUtilities(file.toString(), settings);
            String exportData[][] = new String[rowCount][colCount];

            int colIdx = 0;

            DcField field;
            for (int fieldIdx : getFields()) {
                if (isCanceled()) break;

                field = DcModules.get(moduleIdx).getField(fieldIdx);
                if (field != null) {
                    exportData[0][colIdx] = field.getSystemName();
                    colIdx++;
                }
            }

            String s;
            Connector conn = DcConfig.getInstance().getConnector();

            int rowIdx = 1;


            for (String item : items) {

                colIdx = 0;

                DcObject dco = conn.getItem(getModule().getIndex(), item, null);

                if (isCanceled()) break;

                client.notify(DcResources.getText("msgExportingX", dco.toString()));

                Object o;
                s = "";
                for (int fieldIdx : getFields()) {
                    if (isCanceled()) break;

                    field = DcModules.get(moduleIdx).getField(fieldIdx);

                    if (isCanceled()) break;

                    o = dco.getValue(field.getIndex());

                    if (field != null) {
                        s = "";

                        if (field.getFieldType() == UIComponents._PICTUREFIELD) {
                            if (o != null && o.toString().length() >= 10)
                                s = utilities.getImageURL((Picture) dco.getValue(field.getIndex()));

                        } else if (o instanceof Collection &&
                            DcModules.get(field.getReferenceIdx()).getType() == DcModule._TYPE_ASSOCIATE_MODULE) {

                            for (DcObject subDco : (Collection<DcObject>) o) {
                                if (subDco instanceof DcMapping)
                                    subDco = ((DcMapping) subDco).getReferencedObject();

                                if (subDco != null) {
                                    s += (s.length() > 0 ? ", " : "");
                                    s += ((DcAssociate) subDco).getNameNormal();
                                }
                            }
                        } else {
                            s = dco.getDisplayString(field.getIndex());
                        }
                        exportData[rowIdx][colIdx] = s;
                        colIdx++;
                    }
                }
                rowIdx++;
                client.notifyProcessed();
            }

            return exportData;
        }

    }
}

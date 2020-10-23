package au.org.greekwelfaresa.idempiere.autogen;

import static org.adempiere.base.event.IEventManager.EVENT_DATA;
import static org.adempiere.base.event.IEventTopics.PO_AFTER_CHANGE;
import static org.adempiere.base.event.IEventTopics.PO_AFTER_NEW;

import org.compiere.model.I_AD_Column;
import org.compiere.model.MColumn;
import org.compiere.model.MTable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

@Component(property = { EventConstants.EVENT_TOPIC + "=" + PO_AFTER_CHANGE,
		EventConstants.EVENT_TOPIC + "=" + PO_AFTER_NEW,
		EventConstants.EVENT_FILTER + "=(tableName=" + I_AD_Column.Table_Name + ")" })
public class ColumnListener implements EventHandler {

	@Reference
	TableMap tableNameMap;
	
	@Override
	public void handleEvent(Event event) {
		MColumn column = (MColumn) event.getProperty(EVENT_DATA);
		MTable table = (MTable)column.getAD_Table();
		tableNameMap.updateTable(table, column);
	}
}

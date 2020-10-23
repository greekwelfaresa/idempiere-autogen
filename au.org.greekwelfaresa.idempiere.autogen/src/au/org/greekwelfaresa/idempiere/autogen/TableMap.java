package au.org.greekwelfaresa.idempiere.autogen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.adempiere.base.DefaultModelFactory;
import org.adempiere.base.IModelFactory;
import org.compiere.Adempiere;
import org.compiere.model.MColumn;
import org.compiere.model.MEntityType;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.Trx;
import org.compiere.util.TrxEventListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = TableMap.class)
public class TableMap {

	Map<String, Path> pathMap;
	Map<String, Path> assertPathMap;

	CLogger log = CLogger.getCLogger(TableMap.class);

	@Reference
	List<IModelFactory> factories;

	@Activate
	void activate() {
		System.err.println("Activating tablemap");
		System.err.println("factories: " + factories);
		pathMap = new HashMap<>();
		assertPathMap = new HashMap<>();
		for (Object keyObj : System.getProperties().keySet()) {
			String key = (String)keyObj;
			if (key.startsWith("greekwelfaresa.idempiere.autogen.path.")) {
				String entityType = key.replace("greekwelfaresa.idempiere.autogen.path.", "");
				String path = System.getProperty(key);
				pathMap.put(entityType, Paths.get(path));
			} else if (key.startsWith("greekwelfaresa.idempiere.autogen.assertpath.")) {
				String entityType = key.replace("greekwelfaresa.idempiere.autogen.assertpath.", "");
				String path = System.getProperty(key);
				assertPathMap.put(entityType, Paths.get(path));
			}
		}
	}
	
	static class Listener implements TrxEventListener {

		final Runnable onCommit;

		Listener(Runnable onCommit) {
			this.onCommit = onCommit;
		}

		@Override
		public void afterCommit(Trx trx, boolean success) {
			try {
				if (success) {
					Adempiere.getThreadPoolExecutor().execute(onCommit);
				}
			} finally {
				trx.removeTrxEventListener(this);
			}
		}

		@Override
		public void afterRollback(Trx trx, boolean success) {
			trx.removeTrxEventListener(this);
		}

		@Override
		public void afterClose(Trx trx) {
			trx.removeTrxEventListener(this);
		}
	}

	void updateTable(MTable table) {
		updateTable(table, null);
	}

	static Class<?> findSuperInterface(Class<?> type) {
		if (type == null) {
			return null;
		}
		for (Class<?> iface : type.getInterfaces()) {
			try {
				iface.getField("Table_Name");
				return iface;
			} catch (NoSuchFieldException e) {}
		}
		return findSuperInterface(type.getSuperclass());
	}

	
	void updateTable(MTable table, MColumn column) {
		System.err.println("updateTable: " + table.getTableName());
		final String entityType = table.getEntityType();
		final String columnEntityType = column == null ? entityType : column.getEntityType();
		final Path path = pathMap.get(columnEntityType);
		System.err.println("entity type: " + entityType + ", column: " + columnEntityType + ", path: " + path);
		if (path != null) {
			MEntityType et = MEntityType.get(table.getCtx(), columnEntityType);
			final String tableName = table.getTableName();
			final Object oldTableName = table.get_ValueOld(MTable.COLUMNNAME_TableName);
			final String modelPackage = et.getModelPackage();

			Map<String, MEntityType> entityMap = new HashMap<>();
			Map<String, MEntityType> modelMap = new HashMap<>();
			
			Stream.of(table.getColumns(false)).map(MColumn::getEntityType).map(x -> MEntityType.get(table.getCtx(), x)).forEach(entity -> {
				entityMap.put(entity.getEntityType(), entity);
				modelMap.put(entity.getModelPackage(), entity);
			});
			
			System.err.println(String.format("stuff: %s, %s, %s", tableName, oldTableName, modelPackage));

			Class<?> trySuperClass = null;
			if (entityType.equals(columnEntityType)) {
				
			} else {
				final int columnEntityPriority = et.get_ValueAsInt("Priority");
				ListIterator<IModelFactory> iter = factories.listIterator(factories.size());
				OUTER: while (iter.hasPrevious()) {
					IModelFactory current = iter.previous();
					System.err.println("Attempting factory: " + current);
					Class<?> poClass = current.getClass(tableName);
					System.err.println("===> poClass: " + poClass);
					if (poClass == null) {
						continue;
					}
				
					if (!PO.class.isAssignableFrom(poClass)) {
						log.log(Level.SEVERE,
								() -> "poClass for table " + tableName + " is not a subclass of " + PO.class);
						return;
					}
					System.err.println("Checking DefaultModelFactory");
					if (current.getClass() == DefaultModelFactory.class) {
						trySuperClass = poClass;
						break;
					}
					final String pkg = poClass.getPackage().getName();
					MEntityType currentEntity = modelMap.get(pkg);
					if (currentEntity == null) {
						System.err.println("currentEntity was null");
						continue;
					}
					
					if (currentEntity.getEntityType().equals(columnEntityType)) {
						System.err.println("Attempting class: " + trySuperClass);
						while (iter.hasPrevious()) {
							current = iter.previous();
							System.err.println("descended factory: " + current);
							poClass = current.getClass(tableName);
							if (poClass == null) {
								continue;
							}
							if (!PO.class.isAssignableFrom(poClass)) {
								log.log(Level.SEVERE,
										() -> "poClass for table " + tableName + " is not a subclass of " + PO.class);
								return;
							}
							trySuperClass = poClass;
							break OUTER;
						}
					} else if (currentEntity.get_ValueAsInt("Priority") < columnEntityPriority) {
						System.err.println("This bit");
						trySuperClass = poClass;
						break;
					}
					System.err.println("No bit");
				}
			}
			final String superClass = trySuperClass == null ? null : trySuperClass.getName();
			String trySuperInterface = null;
			if (trySuperClass != null) {
				Class<?> iface = findSuperInterface(trySuperClass);
				trySuperInterface = iface.getName();
			}
			final String superInterface = trySuperInterface;
			System.err.println("Superclass should be: " + superClass);

			final String columnFilter = "EntityType in ('" + columnEntityType + "')";
			System.err.println("entity type: " + columnEntityType + ", " + et);
			System.err.println("callback for table: " + tableName + " total " + columnFilter);
			System.err.println("Generating interface: " + path.toString() + ", " + et.getModelPackage());

			Runnable r = () -> {
				String packagePath = modelPackage.replace(".", File.separator);
				Path fullPath = path.resolve(packagePath);
				try {
					Files.createDirectories(fullPath);
					final String directory = fullPath.toString();
					new ModelInterfaceGenerator(table.get_ID(), directory, modelPackage, columnFilter, superInterface);
					new ModelClassGenerator(table.get_ID(), directory, modelPackage, columnFilter, superClass);
					if (oldTableName != null && !tableName.equals(oldTableName)) {
						try {
							final String iFaceName = "I_" + oldTableName + ".java";
							log.log(Level.WARNING, () -> "Deleting " + iFaceName);
							Files.deleteIfExists(fullPath.resolve(iFaceName));
							final String className = "X_" + oldTableName + ".java";
							log.log(Level.WARNING, () -> "Deleting " + className);
							Files.deleteIfExists(fullPath.resolve(className));
						} catch (IOException e) {
							log.saveError("Couldn't delete old file", e);
						}
					}
					final Path assertPath = assertPathMap.get(columnEntityType);
					if (assertPath != null) {
						System.err.println("asserPath: " + assertPath);
						final String assertRootPackage = modelPackage + ".assertions";
						System.err.println("assert root packasge: " + assertRootPackage);
						final String assertRootPath = assertRootPackage.replace(".", File.separator);
						System.err.println("assert root patss: " + assertRootPath);
						Path fullAssertRootPath = assertPath.resolve(assertRootPath);
						System.err.println("full assert root path: " + fullAssertRootPath);
						Files.createDirectories(fullAssertRootPath);
						System.err.println("assert root package: " + fullAssertRootPath);
						new ModelAssertionGenerator(table.get_ID(), fullAssertRootPath, modelPackage, assertRootPackage, superClass, columnFilter);
						ModelAssertionGenerator.writeAssertionsEntryPoint(fullAssertRootPath, columnEntityType, modelPackage, assertRootPackage);
					}
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			};
			Trx trx = Trx.get(table.get_TrxName(), false);
			if (trx == null) {
				// No transaction, call the model generator directly.
				r.run();
			} else {
				// If it's running as part of a transaction, we can't call the model generator
				// until the transaction is committed or else we won't have the most up-to-date
				// data.
				trx.addTrxEventListener(new Listener(r));
			}

		} else {
			System.err.println("No model generation for entity type: " + columnEntityType);
		}
	}
}

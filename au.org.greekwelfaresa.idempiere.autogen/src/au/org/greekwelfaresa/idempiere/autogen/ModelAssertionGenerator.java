/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 * Contributor(s): Carlos Ruiz - globalqss                                    *
 *                 Teo Sarca - www.arhipac.ro                                 *
 *                 Trifon Trifonov                                            *
 *****************************************************************************/
package au.org.greekwelfaresa.idempiere.autogen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Level;

import org.adempiere.exceptions.DBException;
import org.adempiere.util.ModelInterfaceGenerator;
import org.compiere.Adempiere;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;

/**
 * Generate Assertions Classes for auto-generated models.
 *
 * @author Fr Jeremy Krieg
 */
public class ModelAssertionGenerator {
	static List<String> processedAssertions = new ArrayList<>();
	static List<String> processedModels = new ArrayList<>();

	private String className;
	private String tableName;
	private String assertionClassName;
	private String entityTypeFilter;

	/**
	 * Generate PO Class
	 * 
	 * @param AD_Table_ID      table id
	 * @param directory        directory
	 * @param assertionPackage package name
	 * @param entityTypeFilter
	 * @throws IOException 
	 */
	public ModelAssertionGenerator(int AD_Table_ID, Path directory, String modelPackage, String assertionPackage, String superClass,
			String entityTypeFilter) throws IOException {
		this.entityTypeFilter = entityTypeFilter;

		// Save
		if (!Files.isDirectory(directory)) {
			throw new IllegalArgumentException("Not a directory: " + directory);
		}

		String sql = "SELECT TableName FROM AD_Table WHERE AD_Table_ID=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, AD_Table_ID);
			rs = pstmt.executeQuery();
			if (rs.next()) {
				tableName = rs.getString(1);
			}
		} catch (SQLException e) {
			throw new DBException(e, sql);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		if (tableName == null)
			throw new RuntimeException("TableName not found for ID=" + AD_Table_ID);
		//
		className = "X_" + tableName;
		final String modelClassFQN = modelPackage + "." + className;
		addImportClass(modelClassFQN);
		final String lcTableName = tableName.toLowerCase();
		directory = directory.resolve(lcTableName);
		if (!Files.exists(directory)) {
			Files.createDirectories(directory);
		}

		assertionPackage = assertionPackage + "." + lcTableName;
		this.packageName = assertionPackage;
		assertionClassName = tableName + "Assert";
		final String assertionClassFQN = packageName + "." + assertionClassName;
		
		StringBuilder sb = createColumns(AD_Table_ID);

		System.err.println("superdooperclass: " + superClass);
		// Header
		createHeader(AD_Table_ID, sb, assertionPackage, superClass);

		writeToFile(sb, directory.resolve("Abstract" + assertionClassName + ".java"));
		writeConcreteAssertion(directory, modelPackage);
		processedAssertions.add(assertionClassFQN);
		processedModels.add(modelClassFQN);
	}

	void writeConcreteAssertion(Path directory, String modelPackage) {
		Path file = directory.resolve(assertionClassName + ".java");

		s_importClasses.clear();
		StringBuilder b = new StringBuilder()
				.append("/** Generated Assertion Class - DO NOT CHANGE */").append(NL).append("package ")
				.append(packageName).append(";").append(NL).append(NL);

		addImportClass(javax.annotation.Generated.class);
		addImportClass(modelPackage + '.' + className);
		createImports(b);

		// Class
		b.append("/** Generated assertion class for ").append(tableName).append(NL)
				.append(" *  @author idempiere-test model assertion generator").append(NL).append(" *  @version ")
				.append(Adempiere.MAIN_VERSION).append(" - $Id: 3768645af232713251cd997d610a10cb88547fe3 $ */").append(NL).append("@Generated(\"")
				.append(ModelAssertionGenerator.class).append("\")").append(NL).append("public class ")
				.append(assertionClassName).append(" extends Abstract").append(assertionClassName).append('<').append(assertionClassName).append(", ").append(className).append('>').append(NL).append("{")
				.append(NL)

				// Standard Constructor
				.append(NL).append("    /** Standard Constructor */").append(NL).append("    public ")
				.append(assertionClassName).append(" (").append(className).append(" actual)").append(NL).append("    {")
				.append(NL).append("      super (actual, ").append(assertionClassName).append(".class);").append(NL).append("    }").append(NL).append(NL);
		// Constructor End

		b.append("}");
		
		writeToFile(b, file);
	}

	public static final String NL = "\n";

	/** Logger */
	private static CLogger log = CLogger.getCLogger(ModelAssertionGenerator.class);

	/** Package Name */
	private String packageName = "";

	/**
	 * Add Header info to buffer
	 * 
	 * @param AD_Table_ID table
	 * @param sb          buffer
	 * @param mandatory   init call for mandatory columns
	 * @param packageName package name
	 * @return class name
	 */
	private String createHeader(int AD_Table_ID, StringBuilder sb, String packageName, String superClass) {
		//
		StringBuilder start = new StringBuilder()
				.append("/** Generated Assertion Class - DO NOT CHANGE */").append(NL).append("package ")
				.append(packageName).append(";").append(NL).append(NL);

		addImportClass(javax.annotation.Generated.class);
		String simpleSuper = null;
		String superPkg = null;
		if (superClass == null) {
			superPkg = "au.org.greekwelfaresa.idempiere.test.assertj.po";
			simpleSuper = "AbstractPOAssert";
		} else if (superClass.matches("^org\\.(?:co|ade|ide)mpiere.*$")) {
			superPkg = "au.org.greekwelfaresa.idempiere.test.assertj." + tableName.toLowerCase();
			simpleSuper = "Abstract" + tableName + "Assert";
		} else {
			superPkg = superClass.replaceFirst("\\.[^.]*$", ".assertion.") + tableName.toLowerCase();
			simpleSuper = "Abstract" + tableName + "Assert";
		}
		System.err.println("simplePkg: " + superPkg +", " + simpleSuper);
		createImports(start);
		// Class
		start.append("/** Generated assertion class for ").append(tableName).append(NL)
				.append(" *  @author idempiere-test (generated) ").append(NL).append(" *  @version ")
				.append(Adempiere.MAIN_VERSION).append(" - $Id: 3768645af232713251cd997d610a10cb88547fe3 $ */").append(NL).append("@Generated(\"")
				.append(ModelAssertionGenerator.class).append("\")").append(NL).append("public abstract class Abstract")
				.append(assertionClassName).append("<SELF extends Abstract").append(assertionClassName)
				.append("<SELF, ACTUAL>, ACTUAL extends ").append(className).append('>').append(NL).append("\textends ")
				.append(superPkg).append('.').append(simpleSuper).append("<SELF, ACTUAL>").append(NL).append("{")
				.append(NL)

				// Standard Constructor
				.append(NL).append("    /** Standard Constructor */").append(NL).append("    public Abstract")
				.append(assertionClassName).append(" (ACTUAL actual, Class<SELF> selfType)").append(NL).append("    {")
				.append(NL).append("      super (actual, selfType);").append(NL).append("    }").append(NL).append(NL);
		// Constructor End

		String end = "}";
		//
		sb.insert(0, start);
		sb.append(end);

		return assertionClassName;
	}

	/**
	 * Create Column access methods
	 * 
	 * @param AD_Table_ID table
	 * @param mandatory   init call for mandatory columns
	 * @return set/get method
	 */
	private StringBuilder createColumns(int AD_Table_ID) {
		int buttonReferenceId = DB.getSQLValue(null, "select AD_Reference_ID from AD_Reference where Name=?", "Button");
		StringBuilder sb = new StringBuilder();
		String sql = "SELECT c.ColumnName, c.IsUpdateable, c.IsMandatory," // 1..3
				+ " c.AD_Reference_ID, c.AD_Reference_Value_ID, DefaultValue, SeqNo, " // 4..7
				+ " c.FieldLength, c.ValueMin, c.ValueMax, c.VFormat, c.Callout, " // 8..12
				+ " c.Name, c.Description, c.ColumnSQL, c.IsEncrypted, c.IsKey, c.IsIdentifier, " // 13..18
				+ " c.AD_Column_ID, c.isParent, c.FieldLength, c.IsTranslated, " // 19..22
				+ " c.IsAllowLogging, c.IsAllowCopy " // 23..24
				+ "FROM AD_Column c " + "WHERE c.AD_Table_ID=?"
				+ " AND c.ColumnName NOT IN ('AD_Client_ID', 'AD_Org_ID', 'IsActive', 'Created', 'CreatedBy', 'Updated', 'UpdatedBy')"
				+ " AND c.AD_Reference_ID<>" + buttonReferenceId + " AND c.IsActive='Y'" + " AND " + entityTypeFilter
				+ " ORDER BY c.ColumnName";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, AD_Table_ID);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				String columnName = rs.getString(1);
				boolean isUpdateable = "Y".equals(rs.getString(2));
				boolean isMandatory = "Y".equals(rs.getString(3));
				int displayType = rs.getInt(4);
				int AD_Reference_Value_ID = rs.getInt(5);
				String defaultValue = rs.getString(6);
				int fieldLength = rs.getInt(8);
				String ValueMin = rs.getString(9);
				String ValueMax = rs.getString(10);
				String VFormat = rs.getString(11);
				String Callout = rs.getString(12);
				String Name = rs.getString(13);
				String Description = rs.getString(14);
				String ColumnSQL = rs.getString(15);
				boolean virtualColumn = ColumnSQL != null && ColumnSQL.length() > 0;
				boolean IsEncrypted = "Y".equals(rs.getString(16));
				boolean IsKey = "Y".equals(rs.getString(17));

				//
				sb.append(createColumnMethods(columnName, isUpdateable, isMandatory, displayType, AD_Reference_Value_ID,
						fieldLength, defaultValue, ValueMin, ValueMax, VFormat, Callout, Name, Description,
						virtualColumn, IsEncrypted, IsKey, AD_Table_ID));
			}
		} catch (SQLException e) {
			throw new DBException(e, sql);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		return sb;
	} // createColumns

	/**
	 * Create set/get methods for column
	 * 
	 * @param columnName      column name
	 * @param isUpdateable    updateable
	 * @param isMandatory     mandatory
	 * @param displayType     display type
	 * @param AD_Reference_ID validation reference
	 * @param fieldLength     int
	 * @param defaultValue    default value
	 * @param ValueMin        String
	 * @param ValueMax        String
	 * @param VFormat         String
	 * @param Callout         String
	 * @param Name            String
	 * @param Description     String
	 * @param virtualColumn   virtual column
	 * @param IsEncrypted     stored encrypted
	 * @return set/get method
	 */
	private String createColumnMethods(String columnName, boolean isUpdateable, boolean isMandatory, int displayType,
			int AD_Reference_ID, int fieldLength, String defaultValue, String ValueMin, String ValueMax, String VFormat,
			String Callout, String Name, String Description, boolean virtualColumn, boolean IsEncrypted, boolean IsKey,
			int AD_Table_ID) {
		Class<?> clazz = ModelInterfaceGenerator.getClass(columnName, displayType, AD_Reference_ID);

		// Skip columns in the base class.
		switch (columnName) {
		case "AD_Client_ID":
		case "AD_Org_ID":
		case "UpdatedBy":
		case "Updated":
		case "CreatedBy":
		case "Created":
			return "";
		}

		if (DisplayType.isLOB(displayType)) {
			return "";
		}

		// Set ********
		StringBuilder sb = new StringBuilder();

		sb.append(NL);
		// Integer
		if (clazz.equals(Integer.class)) {
			sb.append("\tpublic SELF has").append(columnName).append("(int expected)").append(NL).append("\t{")
					.append(NL).append("\t\tisNotNull();").append(NL).append("\t\tint actualField = actual.get")
					.append(columnName).append("();").append(NL).append("\t\tif (expected != actualField) {").append(NL)
					.append("\t\t\tfailWithActualExpectedAndMessage(actualField, expected, \"\\nExpecting PO: \\n  <%s>\\n to have ")
					.append(columnName).append(": <%s>\\nbut it was: <%s>\",").append(NL)
					.append("\t\t\t\tgetPODescription(), expected, actualField);").append(NL).append("\t\t}").append(NL)
					.append("\t\treturn myself;").append(NL)
					/**/
					.append("\t}").append(NL);
		} else if (clazz.equals(Boolean.class)) {
			String baseField = columnName.replaceFirst("^[Ii][Ss]", "");
			sb.append("\tpublic SELF is").append(baseField).append("()").append(NL).append("\t{").append(NL)
					.append("\t\tisNotNull();").append(NL).append("\t\tif (!actual.is").append(baseField)
					.append("()) {").append(NL).append("\t\t\tfailWithMessage(\"\\nExpecting PO:\\n  <%s>\\nto be ")
					.append(baseField).append("\\nbut it was not\",").append(NL).append("\t\t\t\tgetPODescription());")
					.append(NL).append("\t\t}").append(NL).append("\t\treturn myself;").append(NL)
					/**/
					.append("\t}").append(NL).append(NL).append("\tpublic SELF isNot").append(baseField).append("()")
					.append(NL).append("\t{").append(NL).append("\t\tisNotNull();").append(NL)
					.append("\t\tif (actual.is").append(baseField).append("()) {").append(NL)
					.append("\t\t\tfailWithMessage(\"\\nExpecting PO: \\n  <%s>\\n to not be ").append(baseField)
					.append("\\nbut it was\",").append(NL).append("\t\t\t\tgetPODescription());").append(NL)
					.append("\t\t}").append(NL).append("\t\treturn myself;").append(NL)
					/**/
					.append("\t}").append(NL);
		} else if (clazz.equals(Timestamp.class)) {
			sb.append("\tpublic SELF has").append(columnName).append("(Object expected)").append(NL).append("\t{")
					.append(NL).append("\t\tisNotNull();").append(NL).append("\t\tdateAssert(\"").append(columnName)
					.append("\", actual.get").append(columnName).append("(), expected);").append(NL)
					.append("\t\treturn myself;").append(NL).append("\t}").append(NL).append(NL);
		} else {
			// Additional methods for BigDecimal comparison
			if (clazz.equals(BigDecimal.class)) {
				sb.append("\tpublic SELF has").append(columnName).append("(Object expected)").append(NL).append("\t{")
						.append(NL).append("\t\tisNotNull();").append(NL).append("\t\tbdAssert(\"").append(columnName)
						.append("\", actual.get").append(columnName).append("(), expected);").append(NL)
						.append("\t\treturn myself;").append(NL).append("\t}").append(NL).append(NL);
			} else {
				addImportClass(clazz);
				addImportClass(Objects.class);
				sb.append("\tpublic SELF has").append(columnName).append("(").append(clazz.getSimpleName())
						.append(" expected)").append(NL).append("\t{").append(NL).append("\t\tisNotNull();").append(NL)
						.append("\t\t").append(clazz.getSimpleName()).append(" actualField = actual.get")
						.append(columnName).append("();").append(NL)
						.append("\t\tif (!Objects.equals(expected, actualField)) {").append(NL)
						.append("\t\t\tfailWithActualExpectedAndMessage(actualField, expected, \"\\nExpecting PO: \\n  <%s>\\n to have ")
						.append(columnName).append(": <%s>\\nbut it was: <%s>\",").append(NL)
						.append("\t\t\t\tgetPODescription(), expected, actualField);").append(NL).append("\t\t}")
						.append(NL).append("\t\treturn myself;").append(NL)
						/**/
						.append("\t}").append(NL);
			}
		}
		return sb.toString();
	} // createColumnMethods

	/**************************************************************************
	 * Write to file
	 * 
	 * @param sb       string buffer
	 * @param fileName file name
	 */
	private static void writeToFile(StringBuilder sb, Path filename) {
		try {
			File out = filename.toFile();
			Writer fw = new OutputStreamWriter(new FileOutputStream(out, false), "UTF-8");
			for (int i = 0; i < sb.length(); i++) {
				char c = sb.charAt(i);
				// after
				if (c == ';' || c == '}') {
					fw.write(c);
					if (sb.substring(i + 1).startsWith("//")) {
						// fw.write('\t');
					} else {
						// fw.write(NL);
					}
				}
				// before & after
				else if (c == '{') {
					// fw.write(NL);
					fw.write(c);
					// fw.write(NL);
				} else
					fw.write(c);
			}
			fw.flush();
			fw.close();
			float size = out.length();
			size /= 1024;
			StringBuilder msgout = new StringBuilder().append(out.getAbsolutePath()).append(" - ").append(size)
					.append(" kB");
			System.out.println(msgout.toString());
		} catch (Exception ex) {
			log.log(Level.SEVERE, filename.toString(), ex);
			throw new RuntimeException(ex);
		}
	}

	/** Import classes */
	private Collection<String> s_importClasses = new TreeSet<String>();

	/**
	 * Add class name to class import list
	 * 
	 * @param className
	 */
	private void addImportClass(String className) {
		if (className == null || (className.startsWith("java.lang.") && !className.startsWith("java.lang.reflect."))
				|| className.startsWith(packageName + "."))
			return;
		for (String name : s_importClasses) {
			if (className.equals(name))
				return;
		}
		s_importClasses.add(className);
	}

	/**
	 * Add class to class import list
	 * 
	 * @param cl
	 */
	private void addImportClass(Class<?> cl) {
		if (cl.isArray()) {
			cl = cl.getComponentType();
		}
		if (cl.isPrimitive())
			return;
		addImportClass(cl.getCanonicalName());
	}

	/**
	 * Generate java imports
	 * 
	 * @param sb
	 */
	private void createImports(StringBuilder sb) {
		for (String name : s_importClasses) {
			sb.append("import ").append(name).append(";").append(NL);
		}
		sb.append(NL);
	}

	/**
	 * String representation
	 * 
	 * @return string representation
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder("ModelAssertionsGenerator[").append("]");
		return sb.toString();
	}

	static void writeAssertionsEntryPoint(Path directory, String entityType, String modelPackageName, String assertionsPackageName) {
		final String staticClassName = entityType + "ModelAssertions";
		final String softClassName = entityType + "ModelSoftAssertionsProvider";
		StringBuilder sb = new StringBuilder()
				.append("/** Generated Assertion Class - DO NOT CHANGE */").append(NL).append("package ")
				.append(assertionsPackageName).append(";").append(NL).append(NL)
				.append("import javax.annotation.Generated;").append(NL);

		StringBuilder softSB = new StringBuilder()
				.append("/** Generated Assertion Class - DO NOT CHANGE */").append(NL).append("package ")
				.append(assertionsPackageName).append(";").append(NL).append(NL)
				.append("import javax.annotation.Generated;").append(NL)
				.append("import org.assertj.core.api.SoftAssertionsProvider;").append(NL);

		// Class
		sb.append("/** Generated assertions entry point").append(NL).append(" *  @author idempiere-test model assertion generator")
				.append(NL).append(" *  @version ").append(Adempiere.MAIN_VERSION).append(" - $Id: 3768645af232713251cd997d610a10cb88547fe3 $ */").append(NL)
				.append("@Generated(\"").append(ModelAssertionGenerator.class).append("\")").append(NL)
				.append("public class ").append(staticClassName).append(" {").append(NL).append("\tprivate ").append(staticClassName).append("() {}").append(NL)
				.append(NL);

		// Soft Assertions class.
		softSB.append("/** Generated soft assertions entry point").append(NL)
				.append(" *  @author idempiere-test model assertion generator ").append(NL).append(" *  @version ")
				.append(Adempiere.MAIN_VERSION).append(" - $Id: 3768645af232713251cd997d610a10cb88547fe3 $ */").append(NL).append("@Generated(\"")
				.append(ModelAssertionGenerator.class).append("\")").append(NL)
				.append("public interface ").append(softClassName).append(" extends SoftAssertionsProvider {").append(NL);

		
		String sql = "SELECT DISTINCT TableName FROM AD_Table t, AD_Column c WHERE t.AD_Table_ID=c.AD_Table_ID and c.EntityType=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setString(1, entityType);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				final String tableName = rs.getString(1);
				final String model = modelPackageName + ".X_" + tableName;
				final String assertion = assertionsPackageName + "." + tableName.toLowerCase() + "." + tableName + "Assert";

				sb.append(NL).append("\tpublic static ").append(assertion).append(" assertThat(").append(model)
						.append(" a) {").append(NL).append("\t\treturn new ").append(assertion).append("(a);").append(NL)
						.append("\t}").append(NL);

				softSB.append(NL).append("\tdefault ").append(assertion).append(" assertThat(").append(model)
						.append(" a) {").append(NL).append("\t\treturn proxy(").append(assertion).append(".class, ")
						.append(model).append(".class, a);").append(NL).append("\t}").append(NL);
			}
		} catch (SQLException e) {
			throw new DBException(e, sql);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		sb.append('}');
		softSB.append('}');
		writeToFile(sb, directory.resolve(staticClassName + ".java"));
		writeToFile(softSB, directory.resolve(softClassName + ".java"));
	}
}

package au.org.greekwelfaresa.idempiere.autogen.test;

import java.time.Instant;

import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.compiere.model.MBPartner;
import org.compiere.model.MColumn;
import org.compiere.model.MElement;
import org.compiere.model.MEntityType;
import org.compiere.model.MTable;

import au.org.greekwelfaresa.idempiere.test.assertj.IDSoftAssertions;
import au.org.greekwelfaresa.idempiere.test.common.annotation.InjectIDempiereEnv;
import au.org.greekwelfaresa.idempiere.test.common.env.IDempiereEnv;
import au.org.greekwelfaresa.idempiere.test.junit5.IDempiereEnvExtension;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
@ExtendWith(IDempiereEnvExtension.class)
public class AutoGenTest {

	@InjectIDempiereEnv
	static IDempiereEnv ENV;
	
	@InjectIDempiereEnv
	IDempiereEnv env;

	static MEntityType ET;
	static int NAME_ID;
	static int DESCRIPTION_ID;
	MTable tb;
	MColumn c1;
	MColumn c2;

	@BeforeAll
	public static void beforeAll() {
		ET = ENV.createPO(MEntityType.class);
		ET.setEntityType("ME");
		ET.setName("My Extension");
		ET.setDescription("Description of my extension");
		ET.setModelPackage("my.model.pkg");
		ET.saveEx();
		NAME_ID = ENV.queryFirstID(MElement.class, "Name='Name'");
		DESCRIPTION_ID = ENV.queryFirstID(MElement.class, "Name='Description'");
	}
	
	@BeforeEach
	public void before() {
		
		tb = env.createPO(MTable.class);
		c1 = env.createPO(MColumn.class);
		c2 = env.createPO(MColumn.class);
		tb.setTableName("ME_Table");
		tb.setName("ME Table");
		tb.setEntityType("ME");
		tb.saveEx();

		c1.setEntityType("ME");
		c1.setAD_Element_ID(NAME_ID);
		c1.setName("Name");
		c1.setAD_Table_ID(tb.get_ID());
		c1.saveEx();

		c2.setEntityType("ME");
		c2.setAD_Element_ID(DESCRIPTION_ID);
		c2.setName("Description");
		c2.setAD_Table_ID(tb.get_ID());
		c2.saveEx();
		
	}

	@Test
	public void save_successfullySaves(IDSoftAssertions softly) {
	}
}
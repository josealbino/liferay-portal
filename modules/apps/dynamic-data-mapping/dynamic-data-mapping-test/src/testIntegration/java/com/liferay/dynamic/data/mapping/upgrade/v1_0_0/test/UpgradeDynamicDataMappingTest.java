/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.mapping.upgrade.v1_0_0.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.dynamic.data.mapping.model.DDMContent;
import com.liferay.dynamic.data.mapping.model.DDMFormLayout;
import com.liferay.dynamic.data.mapping.model.DDMStorageLink;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMStructureConstants;
import com.liferay.dynamic.data.mapping.model.DDMStructureLayout;
import com.liferay.dynamic.data.mapping.model.DDMStructureVersion;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.dynamic.data.mapping.model.DDMTemplateConstants;
import com.liferay.dynamic.data.mapping.model.DDMTemplateVersion;
import com.liferay.dynamic.data.mapping.service.DDMContentLocalServiceUtil;
import com.liferay.dynamic.data.mapping.service.DDMStructureLayoutLocalServiceUtil;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalServiceUtil;
import com.liferay.dynamic.data.mapping.service.DDMStructureVersionLocalServiceUtil;
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalServiceUtil;
import com.liferay.dynamic.data.mapping.service.DDMTemplateVersionLocalServiceUtil;
import com.liferay.dynamic.data.mapping.upgrade.DDMServiceUpgrade;
import com.liferay.dynamic.data.mapping.upgrade.v1_0_0.UpgradeDynamicDataMapping;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.upgrade.UpgradeException;
import com.liferay.portal.kernel.upgrade.UpgradeStep;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.ResourcePermission;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.expando.model.ExpandoColumn;
import com.liferay.portlet.expando.model.ExpandoColumnConstants;
import com.liferay.portlet.expando.model.ExpandoRow;
import com.liferay.portlet.expando.model.ExpandoTable;
import com.liferay.portlet.expando.service.ExpandoColumnLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoRowLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoTableLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoValueLocalServiceUtil;
import com.liferay.registry.Registry;
import com.liferay.registry.RegistryUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.skyscreamer.jsonassert.JSONAssert;

/**
 * @author Marcellus Tavares
 * @author Inácio Nery
 */
@RunWith(Arquillian.class)
public class UpgradeDynamicDataMappingTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
		_timestamp = new Timestamp(System.currentTimeMillis());

		setUpClassNameIds();
		setUpPrimaryKeys();
		setUpUpgradeDynamicDataMapping();
	}

	@After
	public void tearDown() throws Exception {
		deleteStructure(_structureId);

		deleteStructure(_parentStructureId);

		deleteTemplate(_templateId);

		deleteContent(_contentId);

		deleteStorageLink(_storageLinkId);
	}

	@Test
	public void testCreateStructureLayout() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			null, read("ddm-structure-text-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		getDDMFormLayout(_structureId, DDMStructureConstants.VERSION_DEFAULT);
	}

	@Test
	public void testCreateStructureVersion() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			null, read("ddm-structure-text-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		getStructureVersion(
			_structureId, DDMStructureConstants.VERSION_DEFAULT);
	}

	@Test
	public void testCreateTemplateVersion() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			null, read("ddm-structure-text-field.xsd"), "xml");

		addTemplate(
			_templateId, _structureId, null,
			read("ddm-template-text-field.ftl"), "ftl",
			DDMTemplateConstants.TEMPLATE_MODE_CREATE);

		_upgradeDynamicDataMapping.upgrade();

		getTemplateVersion(_templateId, DDMTemplateConstants.VERSION_DEFAULT);
	}

	@Test
	public void testUpgradeExpandoWithLocalized() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-localized.xsd"), "expando");

		long classPK = RandomTestUtil.randomLong();

		ExpandoTable expandoTable = ExpandoTableLocalServiceUtil.addTable(
			_group.getCompanyId(), _classNameIdExpandoStorageAdapter,
			String.valueOf(_structureId));

		ExpandoRow expandoRow = ExpandoRowLocalServiceUtil.addRow(
			expandoTable.getTableId(), classPK);

		addStorageLink(_storageLinkId, expandoRow.getRowId(), _structureId);

		ExpandoColumn expandoColumnName =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "Name",
				ExpandoColumnConstants.STRING_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnName.getColumnId(), classPK,
			read("expando-localized-field.xsd"));

		ExpandoColumn expandoColumnFieldsDisplay =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "_fieldsDisplay",
				ExpandoColumnConstants.STRING_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnFieldsDisplay.getColumnId(), classPK,
			read("expando-localized-field-display.xsd"));

		_upgradeDynamicDataMapping.upgrade();

		String expectedData = read("ddm-content-localized.json");

		String actualData = getContentData(expandoRow.getRowId());

		JSONAssert.assertEquals(expectedData, actualData, false);
	}

	@Test
	public void testUpgradeExpandoWithNestedAndRepeatableFields()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-nested-repeatable.xsd"), "expando");

		long classPK = RandomTestUtil.randomLong();

		ExpandoTable expandoTable = ExpandoTableLocalServiceUtil.addTable(
			_group.getCompanyId(), _classNameIdExpandoStorageAdapter,
			String.valueOf(_structureId));

		ExpandoRow expandoRow = ExpandoRowLocalServiceUtil.addRow(
			expandoTable.getTableId(), classPK);

		addStorageLink(_storageLinkId, expandoRow.getRowId(), _structureId);

		ExpandoColumn expandoColumnTextNestedUpper =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "TextNestedUpper",
				ExpandoColumnConstants.STRING_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnTextNestedUpper.getColumnId(), classPK,
			read("expando-nested-repeatable-field-1.xsd"));

		ExpandoColumn expandoColumnTextParent =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "TextParent",
				ExpandoColumnConstants.STRING_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnTextParent.getColumnId(), classPK,
			read("expando-nested-repeatable-field-2.xsd"));

		ExpandoColumn expandoColumnTextRepeateable =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "TextRepeateable",
				ExpandoColumnConstants.STRING_ARRAY_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnTextRepeateable.getColumnId(), classPK,
			read("expando-nested-repeatable-field-3.xsd"));

		ExpandoColumn expandoColumnTextNestedBottom =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "TextNestedBottom",
				ExpandoColumnConstants.STRING_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnTextNestedBottom.getColumnId(), classPK,
			read("expando-nested-repeatable-field-4.xsd"));

		ExpandoColumn expandoColumnFieldsDisplay =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "_fieldsDisplay",
				ExpandoColumnConstants.STRING_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnFieldsDisplay.getColumnId(), classPK,
			read("expando-nested-repeatable-field-display.xsd"));

		_upgradeDynamicDataMapping.upgrade();

		String expectedData = read("ddm-content-nested-repeatable.json");

		String actualData = getContentData(expandoRow.getRowId());

		JSONAssert.assertEquals(expectedData, actualData, false);
	}

	@Test
	public void testUpgradeExpandoWithRepeatableFields() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-text-repeatable-field.xsd"), "expando");

		long classPK = RandomTestUtil.randomLong();

		ExpandoTable expandoTable = ExpandoTableLocalServiceUtil.addTable(
			_group.getCompanyId(), _classNameIdExpandoStorageAdapter,
			String.valueOf(_structureId));

		ExpandoRow expandoRow = ExpandoRowLocalServiceUtil.addRow(
			expandoTable.getTableId(), classPK);

		addStorageLink(_storageLinkId, expandoRow.getRowId(), _structureId);

		ExpandoColumn expandoColumnTextRepeatable =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "TextRepeatable",
				ExpandoColumnConstants.STRING_ARRAY_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnTextRepeatable.getColumnId(), classPK,
			read("expando-value-text-repeatable-field.xsd"));

		ExpandoColumn expandoColumnFieldsDisplay =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "_fieldsDisplay",
				ExpandoColumnConstants.STRING_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnFieldsDisplay.getColumnId(), classPK,
			read("expando-value-text-repeatable-field-display.xsd"));

		_upgradeDynamicDataMapping.upgrade();

		String expectedData = read("ddm-content-text-repeatable-field.json");

		String actualData = getContentData(expandoRow.getRowId());

		JSONAssert.assertEquals(expectedData, actualData, false);
	}

	@Test
	public void testUpgradeExpandoWithTransientRepeatableParent()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-transient-repeatable-parent.xsd"), "expando");

		long classPK = RandomTestUtil.randomLong();

		ExpandoTable expandoTable = ExpandoTableLocalServiceUtil.addTable(
			_group.getCompanyId(), _classNameIdExpandoStorageAdapter,
			String.valueOf(_structureId));

		ExpandoRow expandoRow = ExpandoRowLocalServiceUtil.addRow(
			expandoTable.getTableId(), classPK);

		addStorageLink(_storageLinkId, expandoRow.getRowId(), _structureId);

		ExpandoColumn expandoColumnText =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "Text",
				ExpandoColumnConstants.STRING_ARRAY_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnText.getColumnId(), classPK,
			read("expando-transient-repeatable-parent-field.xsd"));

		ExpandoColumn expandoColumnFieldsDisplay =
			ExpandoColumnLocalServiceUtil.addColumn(
				expandoTable.getTableId(), "_fieldsDisplay",
				ExpandoColumnConstants.STRING_LOCALIZED);

		ExpandoValueLocalServiceUtil.addValue(
			expandoTable.getClassNameId(), expandoTable.getTableId(),
			expandoColumnFieldsDisplay.getColumnId(), classPK,
			read("expando-transient-repeatable-parent-field-display.xsd"));

		_upgradeDynamicDataMapping.upgrade();

		String expectedData = read(
			"ddm-content-transient-repeatable-parent.json");

		String actualData = getContentData(expandoRow.getRowId());

		JSONAssert.assertEquals(expectedData, actualData, false);
	}

	@Test
	public void testUpgradeStructureLocalized() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-localized.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-localized.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructurePermissions() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-boolean-field.xsd"), "xml");

		long resourcePermissionId = RandomTestUtil.randomLong();

		addResourcePermission(
			resourcePermissionId, _structureId, DDMStructure.class.getName());

		_upgradeDynamicDataMapping.upgrade();

		String expectedResourceName = getStructureModelResourceName(
			_classNameIdDDLRecordSet);

		ResourcePermission resourcePermission =
			ResourcePermissionLocalServiceUtil.getResourcePermission(
				resourcePermissionId);

		String actualResourceName = resourcePermission.getName();

		Assert.assertEquals(expectedResourceName, actualResourceName);
	}

	@Test
	public void testUpgradeStructureReferencesDueUpdatedStructureFieldName()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-references-field-name.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read(
			"ddm-structure-references-field-name.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithBooleanField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-boolean-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-boolean-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithDateField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-date-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-date-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithDecimalField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-decimal-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-decimal-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithDocumentLibraryField()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-document-library-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read(
			"ddm-structure-document-library-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithFileUploadField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-file-upload-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read(
			"ddm-structure-file-upload-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithGeolocationField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-geolocation-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read(
			"ddm-structure-geolocation-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithHtmlField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-html-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-html-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithImageField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-image-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-image-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithIntegerField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-integer-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-integer-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test(expected = UpgradeException.class)
	public void testUpgradeStructureWithInvalidFieldNameCharacters1()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-invalid-field-name-characters-1.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();
	}

	@Test(expected = UpgradeException.class)
	public void testUpgradeStructureWithInvalidFieldNameCharacters2()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-invalid-field-name-characters-2.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();
	}

	@Test(expected = UpgradeException.class)
	public void testUpgradeStructureWithInvalidFieldNameCharacters3()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-invalid-field-name-characters-3.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();
	}

	@Test(expected = UpgradeException.class)
	public void testUpgradeStructureWithInvalidFieldNameCharacters4()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-invalid-field-name-characters-4.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();
	}

	@Test(expected = UpgradeException.class)
	public void testUpgradeStructureWithInvalidFieldNameCharacters5()
		throws Exception {

		addStructure(
			_parentStructureId,
			DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-parent.xsd"), "xml");

		addStructure(
			_structureId, _parentStructureId,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-invalid-field-name-characters-5.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();
	}

	@Test
	public void testUpgradeStructureWithLinkToPageField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-link-to-page-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read(
			"ddm-structure-link-to-page-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithNestedFields() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-nested-fields.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-nested-fields.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithNumberField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-number-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-number-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithParentStructure() throws Exception {
		addStructure(
			_parentStructureId,
			DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-parent.xsd"), "xml");

		addStructure(
			_structureId, _parentStructureId,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-child.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-parent.json");

		String actualDefinition = getStructureDefinition(_parentStructureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);

		expectedDefinition = read("ddm-structure-child.json");

		actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithRadioField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-radio-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-radio-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test(expected = UpgradeException.class)
	public void testUpgradeStructureWithSameStructure() throws Exception {
		addStructure(
			_parentStructureId,
			DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-parent.xsd"), "xml");

		addStructure(
			_structureId, _parentStructureId,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-parent.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();
	}

	@Test
	public void testUpgradeStructureWithSelectField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-select-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-select-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithSeparatorField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-separator-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-separator-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithTextAreaField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-text-area-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-text-area-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeStructureWithTextField() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-text-field.xsd"), "xml");

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read("ddm-structure-text-field.json");

		String actualDefinition = getStructureDefinition(_structureId);

		JSONAssert.assertEquals(expectedDefinition, actualDefinition, false);
	}

	@Test
	public void testUpgradeTemplatePermissions() throws Exception {
		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			null, read("ddm-structure-text-field.xsd"), "xml");

		addTemplate(
			_templateId, _structureId, null,
			read("ddm-template-text-field.ftl"), "ftl",
			DDMTemplateConstants.TEMPLATE_MODE_CREATE);

		long resourcePermissionId = RandomTestUtil.randomLong();

		addResourcePermission(
			resourcePermissionId, _templateId, DDMTemplate.class.getName());

		_upgradeDynamicDataMapping.upgrade();

		String expectedResourceName = getTemplateModelResourceName(
			_classNameIdDDLRecordSet);

		ResourcePermission resourcePermission =
			ResourcePermissionLocalServiceUtil.getResourcePermission(
				resourcePermissionId);

		String actualResourceName = resourcePermission.getName();

		Assert.assertEquals(expectedResourceName, actualResourceName);
	}

	@Test
	public void testUpgradeTemplateReferencesDueUpdatedStructureFieldName1()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-references-field-name.xsd"), "xml");

		addTemplate(
			_templateId, _structureId, DDMTemplateConstants.VERSION_DEFAULT,
			read("ddm-template-references-field-name-1.ftl"), "ftl",
			DDMTemplateConstants.TEMPLATE_TYPE_DISPLAY);

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read(
			"ddm-template-references-valid-field-name-1.ftl");

		String actualDefinition = getTemplateScript(_templateId);

		Assert.assertEquals(expectedDefinition, actualDefinition);
	}

	@Test
	public void testUpgradeTemplateReferencesDueUpdatedStructureFieldName2()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-references-field-name.xsd"), "xml");

		addTemplate(
			_templateId, _structureId, DDMTemplateConstants.VERSION_DEFAULT,
			read("ddm-template-references-field-name-2.vm"), "ftl",
			DDMTemplateConstants.TEMPLATE_TYPE_DISPLAY);

		_upgradeDynamicDataMapping.upgrade();

		String expectedDefinition = read(
			"ddm-template-references-valid-field-name-2.vm");

		String actualDefinition = getTemplateScript(_templateId);

		Assert.assertEquals(expectedDefinition, actualDefinition);
	}

	@Test
	public void testUpgradeXMLStorageAdapterWithNestedAndRepeatableFields()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-nested-repeatable.xsd"), "xml");

		addContent(_contentId, read("ddm-content-nested-repeatable.xsd"));

		addStorageLink(_storageLinkId, _contentId, _structureId);

		_upgradeDynamicDataMapping.upgrade();

		String expectedData = read("ddm-content-nested-repeatable.json");

		String actualData = getContentData(_contentId);

		JSONAssert.assertEquals(expectedData, actualData, false);
	}

	@Test
	public void testUpgradeXMLStorageAdapterWithRepeatableFields()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-text-repeatable-field.xsd"), "xml");

		addContent(_contentId, read("ddm-content-text-repeatable-field.xsd"));

		addStorageLink(_storageLinkId, _contentId, _structureId);

		_upgradeDynamicDataMapping.upgrade();

		String expectedData = read("ddm-content-text-repeatable-field.json");

		String actualData = getContentData(_contentId);

		JSONAssert.assertEquals(expectedData, actualData, false);
	}

	@Test
	public void testUpgradeXMLStorageAdapterWithSimpleFields()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-text-field.xsd"), "xml");

		addContent(_contentId, read("ddm-content-text-field.xsd"));

		addStorageLink(_storageLinkId, _contentId, _structureId);

		_upgradeDynamicDataMapping.upgrade();

		String expectedData = read("ddm-content-text-field.json");

		String actualData = getContentData(_contentId);

		JSONAssert.assertEquals(expectedData, actualData, false);
	}

	@Test
	public void testUpgradeXMLStorageAdapterWithTransientRepeatableParent()
		throws Exception {

		addStructure(
			_structureId, DDMStructureConstants.DEFAULT_PARENT_STRUCTURE_ID,
			DDMStructureConstants.VERSION_DEFAULT,
			read("ddm-structure-transient-repeatable-parent.xsd"), "xml");

		addContent(
			_contentId, read("ddm-content-transient-repeatable-parent.xsd"));

		addStorageLink(_storageLinkId, _contentId, _structureId);

		_upgradeDynamicDataMapping.upgrade();

		String expectedData = read(
			"ddm-content-transient-repeatable-parent.json");

		String actualData = getContentData(_contentId);

		JSONAssert.assertEquals(expectedData, actualData, false);
	}

	protected void addContent(long contentId, String data) throws Exception {
		Connection con = DataAccess.getUpgradeOptimizedConnection();

		PreparedStatement ps = null;

		try {
			StringBundler sb = new StringBundler(4);

			sb.append("insert into DDMContent (contentId, groupId, ");
			sb.append("companyId, userId, userName, createDate, ");
			sb.append("modifiedDate, name, description, data_) values (?, ?, ");
			sb.append("?, ?, ?, ?, ?, ?, ?, ?)");

			String sql = sb.toString();

			ps = con.prepareStatement(sql);

			ps.setLong(1, contentId);
			ps.setLong(2, _group.getGroupId());
			ps.setLong(3, _group.getCompanyId());
			ps.setLong(4, TestPropsValues.getUserId());
			ps.setString(5, null);
			ps.setTimestamp(6, _timestamp);
			ps.setTimestamp(7, _timestamp);
			ps.setString(8, DDMStorageLink.class.getName());
			ps.setString(9, StringPool.BLANK);
			ps.setString(10, data);

			ps.executeUpdate();
		}
		finally {
			DataAccess.cleanUp(con, ps);
		}
	}

	protected void addResourcePermission(
			long resourcePermissionId, long structureId, String name)
		throws Exception {

		Connection con = DataAccess.getUpgradeOptimizedConnection();

		PreparedStatement ps = null;

		try {
			StringBundler sb = new StringBundler(4);

			sb.append("insert into ResourcePermission (mvccVersion, ");
			sb.append("resourcePermissionId, companyId, name, scope,  ");
			sb.append("primKey, primKeyId, roleId, ownerId, actionIds ) ");
			sb.append("values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

			String sql = sb.toString();

			ps = con.prepareStatement(sql);

			ps.setLong(1, 0);
			ps.setLong(2, resourcePermissionId);
			ps.setLong(3, _group.getCompanyId());
			ps.setString(4, name);
			ps.setInt(5, ResourceConstants.SCOPE_INDIVIDUAL);
			ps.setString(6, String.valueOf(structureId));
			ps.setLong(7, structureId);
			ps.setLong(8, RandomTestUtil.randomLong());
			ps.setLong(9, TestPropsValues.getUserId());
			ps.setLong(10, 1);

			ps.executeUpdate();
		}
		finally {
			DataAccess.cleanUp(con, ps);
		}
	}

	protected void addStorageLink(
			long storageLinkId, long classPK, long structureId)
		throws Exception {

		Connection con = DataAccess.getUpgradeOptimizedConnection();

		PreparedStatement ps = null;

		try {
			StringBundler sb = new StringBundler(3);

			sb.append("insert into DDMStorageLink (storageLinkId, companyId, ");
			sb.append("classNameId, classPK, structureId) values (?, ?, ?, ");
			sb.append("?, ?)");

			String sql = sb.toString();

			ps = con.prepareStatement(sql);

			ps.setLong(1, storageLinkId);
			ps.setLong(2, _group.getCompanyId());
			ps.setLong(3, _classNameIdDDMContent);
			ps.setLong(4, classPK);
			ps.setLong(5, structureId);

			ps.executeUpdate();
		}
		finally {
			DataAccess.cleanUp(con, ps);
		}
	}

	protected void addStructure(
			long structureId, long parentStructureId, String version,
			String definition, String storageType)
		throws Exception {

		Connection con = DataAccess.getUpgradeOptimizedConnection();

		PreparedStatement ps = null;

		try {
			StringBundler sb = new StringBundler(6);

			sb.append("insert into DDMStructure (structureId, groupId, ");
			sb.append("companyId, userId, userName, versionUserId, ");
			sb.append("versionUserName, createDate, modifiedDate, ");
			sb.append("parentStructureId, classNameId, structureKey, ");
			sb.append("version, name, description, definition, storageType, ");
			sb.append("type_) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ");
			sb.append("?, ?, ?, ?, ?)");

			String sql = sb.toString();

			ps = con.prepareStatement(sql);

			ps.setLong(1, structureId);
			ps.setLong(2, _group.getGroupId());
			ps.setLong(3, _group.getCompanyId());
			ps.setLong(4, TestPropsValues.getUserId());
			ps.setString(5, null);
			ps.setLong(6, TestPropsValues.getUserId());
			ps.setString(7, null);
			ps.setTimestamp(8, _timestamp);
			ps.setTimestamp(9, _timestamp);
			ps.setLong(10, parentStructureId);
			ps.setLong(11, _classNameIdDDLRecordSet);
			ps.setString(12, StringUtil.randomString());
			ps.setString(13, version);
			ps.setString(14, StringUtil.randomString());
			ps.setString(15, StringPool.BLANK);
			ps.setString(16, definition);
			ps.setString(17, storageType);
			ps.setInt(18, DDMStructureConstants.TYPE_DEFAULT);

			ps.executeUpdate();
		}
		finally {
			DataAccess.cleanUp(con, ps);
		}
	}

	protected void addTemplate(
			Long templateId, long structureId, String version, String script,
			String language, String type)
		throws Exception {

		Connection con = DataAccess.getUpgradeOptimizedConnection();

		PreparedStatement ps = null;

		try {
			StringBundler sb = new StringBundler(7);

			sb.append("insert into DDMTemplate (templateId, groupId, ");
			sb.append("companyId, userId, userName, versionUserId, ");
			sb.append("versionUserName, createDate, modifiedDate, ");
			sb.append("classNameId, classPK, resourceClassNameId, ");
			sb.append("templateKey, version, name, mode_, language, script, ");
			sb.append("type_) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ");
			sb.append("?, ?, ?, ?, ?, ?)");

			String sql = sb.toString();

			ps = con.prepareStatement(sql);

			ps.setLong(1, templateId);
			ps.setLong(2, _group.getGroupId());
			ps.setLong(3, _group.getCompanyId());
			ps.setLong(4, TestPropsValues.getUserId());
			ps.setString(5, null);
			ps.setLong(6, TestPropsValues.getUserId());
			ps.setString(7, null);
			ps.setTimestamp(8, _timestamp);
			ps.setTimestamp(9, _timestamp);
			ps.setLong(10, _classNameIdDDMStructure);
			ps.setLong(11, structureId);
			ps.setLong(12, _classNameIdDDLRecordSet);
			ps.setString(13, StringUtil.randomString());
			ps.setString(14, version);
			ps.setString(15, StringUtil.randomString());
			ps.setString(16, DDMTemplateConstants.TEMPLATE_MODE_CREATE);
			ps.setString(17, language);
			ps.setString(18, script);
			ps.setString(19, type);

			ps.executeUpdate();
		}
		finally {
			DataAccess.cleanUp(con, ps);
		}
	}

	protected void deleteContent(long contentId) throws Exception {
		DB db = DBManagerUtil.getDB();

		db.runSQL("delete from DDMContent where contentId = " + contentId);
	}

	protected void deleteStorageLink(long storageLinkId) throws Exception {
		DB db = DBManagerUtil.getDB();

		db.runSQL(
			"delete from DDMStorageLink where storageLinkId = " +
				storageLinkId);
	}

	protected void deleteStructure(long structureId) throws Exception {
		DB db = DBManagerUtil.getDB();

		db.runSQL(
			"delete from DDMStructure where structureId = " + structureId);
	}

	protected void deleteTemplate(long templateId) throws Exception {
		DB db = DBManagerUtil.getDB();

		db.runSQL("delete from DDMTemplate where templateId = " + templateId);
	}

	protected String getBasePath() {
		return "com/liferay/dynamic/data/mapping/dependencies/upgrade/v1_0_0/";
	}

	protected String getContentData(long contentId) throws Exception {
		DDMContent content = DDMContentLocalServiceUtil.getContent(contentId);

		return content.getData();
	}

	protected DDMFormLayout getDDMFormLayout(long structureId, String version)
		throws Exception {

		DDMStructureVersion structureVersion = getStructureVersion(
			structureId, version);

		DDMStructureLayout ddmStructureLayout =
			DDMStructureLayoutLocalServiceUtil.
				getStructureLayoutByStructureVersionId(
					structureVersion.getStructureVersionId());

		return ddmStructureLayout.getDDMFormLayout();
	}

	protected String getStructureDefinition(long structureId) throws Exception {
		DDMStructure structure = DDMStructureLocalServiceUtil.getStructure(
			structureId);

		return structure.getDefinition();
	}

	protected String getStructureModelResourceName(long classNameId)
		throws UpgradeException {

		String className = PortalUtil.getClassName(classNameId);

		String structureModelResourceName = _structureModelResourceNames.get(
			className);

		if (structureModelResourceName == null) {
			throw new UpgradeException(
				"Model " + className + " does not support dynamic data " +
					"mapping structure permission checking");
		}

		return structureModelResourceName;
	}

	protected DDMStructureVersion getStructureVersion(
			long structureId, String version)
		throws Exception {

		return DDMStructureVersionLocalServiceUtil.getStructureVersion(
			structureId, version);
	}

	protected String getTemplateModelResourceName(long classNameId)
		throws UpgradeException {

		String className = PortalUtil.getClassName(classNameId);

		String templateModelResourceName = _templateModelResourceNames.get(
			className);

		if (templateModelResourceName == null) {
			throw new UpgradeException(
				"Model " + className + " does not support dynamic data " +
					"mapping template permission checking");
		}

		return templateModelResourceName;
	}

	protected String getTemplateScript(long templateId) throws Exception {
		DDMTemplate template = DDMTemplateLocalServiceUtil.getTemplate(
			templateId);

		return template.getScript();
	}

	protected DDMTemplateVersion getTemplateVersion(
			long templateId, String version)
		throws Exception {

		return DDMTemplateVersionLocalServiceUtil.getTemplateVersion(
			templateId, version);
	}

	protected Map<Class<?>, UpgradeStep> mapByClass(
		UpgradeStep[] upgradeSteps) {

		Map<Class<?>, UpgradeStep> upgradeStepsMap = new HashMap<>();

		for (UpgradeStep upgradeStep : upgradeSteps) {
			upgradeStepsMap.put(upgradeStep.getClass(), upgradeStep);
		}

		return upgradeStepsMap;
	}

	protected String read(String fileName) throws Exception {
		Class<?> clazz = getClass();

		return StringUtil.read(
			clazz.getClassLoader(), getBasePath() + fileName);
	}

	protected void setUpClassNameIds() {
		_classNameIdDDLRecordSet = PortalUtil.getClassNameId(
			"com.liferay.portlet.dynamicdatalists.model.DDLRecordSet");
		_classNameIdDDMStructure = PortalUtil.getClassNameId(
			"com.liferay.dynamic.data.mapping.model.DDMStructure");
		_classNameIdDDMContent = PortalUtil.getClassNameId(
			"com.liferay.dynamic.data.mapping.model.DDMContent");
		_classNameIdExpandoStorageAdapter = PortalUtil.getClassNameId(
			"com.liferay.portlet.dynamicdatamapping.storage." +
				"ExpandoStorageAdapter");
	}

	protected void setUpPrimaryKeys() {
		_structureId = RandomTestUtil.randomLong();
		_parentStructureId = RandomTestUtil.randomLong();
		_templateId = RandomTestUtil.randomLong();
		_storageLinkId = RandomTestUtil.randomLong();
		_contentId = RandomTestUtil.randomLong();
	}

	protected void setUpUpgradeDynamicDataMapping() {
		Registry registry = RegistryUtil.getRegistry();

		DDMServiceUpgrade ddmServiceUpgrade = registry.getService(
			DDMServiceUpgrade.class);

		Map<Class<?>, UpgradeStep> upgradeStepsMap = mapByClass(
			ddmServiceUpgrade.getUpgradeSteps("1.0.0"));

		_upgradeDynamicDataMapping =
			(UpgradeDynamicDataMapping)upgradeStepsMap.get(
				UpgradeDynamicDataMapping.class);
	}

	private static final Map<String, String> _structureModelResourceNames =
		new HashMap<>();
	private static final Map<String, String> _templateModelResourceNames =
		new HashMap<>();

	static {
		_structureModelResourceNames.put(
			"com.liferay.portlet.documentlibrary.model.DLFileEntryMetadata",
			"com.liferay.portlet.documentlibrary.model.DLFileEntryMetadata-" +
				DDMStructure.class.getName());

		_structureModelResourceNames.put(
			"com.liferay.portlet.documentlibrary.util.RawMetadataProcessor",
			DDMStructure.class.getName());

		_structureModelResourceNames.put(
			"com.liferay.portlet.dynamicdatalists.model.DDLRecordSet",
			"com.liferay.dynamic.data.lists.model.DDLRecordSet-" +
				DDMStructure.class.getName());

		_structureModelResourceNames.put(
			"com.liferay.portlet.journal.model.JournalArticle",
			"com.liferay.journal.model.JournalArticle-" +
				DDMStructure.class.getName());

		_templateModelResourceNames.put(
			"com.liferay.portlet.display.template.PortletDisplayTemplate",
			DDMTemplate.class.getName());

		_templateModelResourceNames.put(
			"com.liferay.portlet.dynamicdatalists.model.DDLRecordSet",
			"com.liferay.dynamic.data.lists.model.DDLRecordSet-" +
				DDMTemplate.class.getName());

		_templateModelResourceNames.put(
			"com.liferay.portlet.journal.model.JournalArticle",
			"com.liferay.journal.model.JournalArticle-" +
				DDMTemplate.class.getName());
	}

	private long _classNameIdDDLRecordSet;
	private long _classNameIdDDMContent;
	private long _classNameIdDDMStructure;
	private long _classNameIdExpandoStorageAdapter;
	private long _contentId;

	@DeleteAfterTestRun
	private Group _group;

	private long _parentStructureId;
	private long _storageLinkId;
	private long _structureId;
	private long _templateId;
	private Timestamp _timestamp;
	private UpgradeDynamicDataMapping _upgradeDynamicDataMapping;

}
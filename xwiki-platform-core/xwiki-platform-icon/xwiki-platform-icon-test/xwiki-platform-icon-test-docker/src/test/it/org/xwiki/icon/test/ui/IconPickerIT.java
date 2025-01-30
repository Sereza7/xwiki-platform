/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.icon.test.ui;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.xwiki.icon.test.po.IconPicker;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of the icon picker.
 *
 * @version $Id$
 * @since 13.4RC1
 */
@UITest
class IconPickerIT 
{
    @Test
    void selectIcon(TestUtils testUtils, TestReference testReference) throws Exception
    {
        testUtils.loginAsSuperAdmin();
        ViewPage viewPage = testUtils.createPage(testReference, """
            {{html}}
            <p>Field 1: <input type='text' id='myPicker'/></p>
            {{/html}}
            {{iconPicker id='myPicker'/}}""");
        assertTrue(viewPage.contentContainsElement(By.xpath(".//input[@id='myPicker']")));
        IconPicker myPicker = new IconPicker("myPicker");
        // Check that the icon picker opens as expected.
        assertFalse(myPicker.isOpen());
        myPicker.openPicker();
        assertTrue(myPicker.isOpen());
        // We check that the picker shows some entries
        assertFalse(myPicker.listShownOptions().isEmpty());
        // Without any input, the first item should always be the same.
        assertEquals(myPicker.listShownOptions().get(0), "add");
    }
}
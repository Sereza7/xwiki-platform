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
package org.xwiki.icon.test.po;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Represents an icon picker.
 *
 * @version $Id$
 * @since 17.1.0RC1
 */
 
public class IconPicker extends BaseElement
{

    private final WebElement input;
    private WebElement container;

    public IconPicker(String id)
    {
        this.input = getDriver().findElementWithoutWaiting(By.id(id));
    }
    
    public void openPicker()
    {
        if (!this.isOpen()) 
        {
            input.click();
            // We wait for the spinner meaning that the picker is loading to disappear.
            // The spinner is the only image that is not wrapped in anything else in the container.
            getDriver().waitUntilElementDisappears(By.cssSelector(".xwikiIconPickerList > img"));
        }
    }
    public boolean isOpen() 
    {
        return !getDriver().findElementsWithoutWaiting(By.className("xwikiIconPickerContainer")).isEmpty();
    }
    
    public List<String> listShownOptions()
    {
        this.container = getDriver().findElementWithoutWaiting(By.className("xwikiIconPickerContainer"));
        List<WebElement> shownOptions = getDriver().findElementsWithoutWaiting(By.cssSelector(".xwikiIconPickerIcon"));
        return shownOptions.stream().map(WebElement::getText).toList();
    }
}
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
package org.xwiki.index.test.ui.docker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xwiki.test.docker.junit5.UITest;

/**
 * All UI tests for the Index feature.
 *
 * @version $Id$
 * @since 11.4RC1
 */
@ExtendWith(DynamicTestConfigurationExtension.class)
@UITest
public class AllIT
{
    @Nested
    @DisplayName("AllDocs Page UI")
    class NestedAllDocsIT extends AllDocsIT
    {
    }

    @Nested
    @DisplayName("Deleted Attachment Page UI")
    class NestedDeletedAttachmentsIT extends DeletedAttachmentsIT
    {
    }

    @Nested
    @DisplayName("Orphaned Pages Page UI")
    class NestedOrphanedPagesIT extends OrphanedPagesIT
    {
    }

    @Nested
    @DisplayName("Documents Macro UI")
    class NestedDocumentsMacroIT extends DocumentsMacroIT
    {
    }

    @Nested
    @DisplayName("Pinned Pages UI")
    class NestedPinnedPagesIT extends PinnedPagesIT
    {
    }

    @Nested
    @DisplayName("Document Tree Macro")
    class NestedDocumentTreeMacroIT extends DocumentTreeMacroIT
    {
    }
}

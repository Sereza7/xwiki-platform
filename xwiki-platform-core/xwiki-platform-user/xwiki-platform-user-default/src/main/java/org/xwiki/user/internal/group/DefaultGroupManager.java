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
package org.xwiki.user.internal.group;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.internal.reference.EntityReferenceFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;
import org.xwiki.user.group.WikiTarget;
import org.xwiki.user.internal.group.AbstractGroupCache.GroupCacheEntry;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiGroupService;

/**
 * Fast access to group membership.
 * 
 * @version $Id$
 * @since 10.8RC1
 */
@Component
@Singleton
public class DefaultGroupManager implements GroupManager
{
    @Inject
    private MemberGroupsCache memberGroupsCache;

    @Inject
    private GroupMembersCache groupMembersCache;

    @Inject
    private WikiGroupCache wikiGroupCache;

    @Inject
    private WikiDescriptorManager wikis;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private EntityReferenceFactory referenceFactory;

    @Inject
    private Logger logger;

    private Collection<String> getSearchWikis(DocumentReference reference, WikiTarget wikiTarget, boolean resolve)
        throws GroupException
    {
        Collection<String> cacheWikis;

        switch (wikiTarget) {
            case ENTITY:
                cacheWikis = Collections.singleton(reference.getWikiReference().getName());
                break;
            case ENTITY_AND_CURRENT:
                cacheWikis = new TreeSet<>();
                cacheWikis.add(reference.getWikiReference().getName());
                cacheWikis.add(this.wikis.getCurrentWikiId());
                break;

            default:
                if (resolve) {
                    try {
                        cacheWikis = this.wikis.getAllIds();
                    } catch (WikiManagerException e) {
                        throw new GroupException("Failed to get all wikis", e);
                    }
                } else {
                    cacheWikis = null;
                }
                break;
        }

        return cacheWikis;
    }

    private Collection<String> getSearchWikis(DocumentReference reference, Collection<?> wikiTarget, boolean resolve)
    {
        Collection<String> searchWikis = new TreeSet<>();

        for (Object wiki : wikiTarget) {
            if (wiki instanceof String) {
                searchWikis.add((String) wiki);
            } else if (wiki instanceof WikiReference) {
                searchWikis.add(((WikiReference) wiki).getName());
            } else if (wiki instanceof WikiTarget) {
                searchWikis.addAll(getSearchWikis(reference, wikiTarget, resolve));
            }
        }

        return searchWikis;
    }

    private Collection<String> getSearchWikis(DocumentReference reference, Object wikiTarget, boolean resolve)
        throws GroupException
    {
        Collection<String> cacheWikis;

        if (wikiTarget instanceof WikiTarget) {
            cacheWikis = getSearchWikis(reference, (WikiTarget) wikiTarget, resolve);
        } else if (wikiTarget instanceof String) {
            cacheWikis = Collections.singleton((String) wikiTarget);
        } else if (wikiTarget instanceof WikiReference) {
            cacheWikis = Collections.singleton(((WikiReference) wikiTarget).getName());
        } else if (wikiTarget instanceof Collection && !((Collection) wikiTarget).isEmpty()) {
            cacheWikis = getSearchWikis(reference, (Collection) wikiTarget, resolve);
        } else if (wikiTarget == null) {
            cacheWikis = getSearchWikis(reference, WikiTarget.ALL, resolve);
        } else {
            throw new GroupException(
                "Unsuported wiki target [" + wikiTarget + "] with class [" + wikiTarget.getClass() + "]");
        }

        return cacheWikis;
    }

    @Override
    public Collection<DocumentReference> getGroups(DocumentReference reference, Object wikiTarget, boolean recurse)
        throws GroupException
    {
        // Guest user never belongs to any group.
        if (reference == null) {
            return Collections.emptyList();
        }
        return getGroups(reference, wikiTarget, recurse, null);
    }

    private Collection<DocumentReference> getGroups(DocumentReference reference, Object wikiTarget, boolean recurse,
        Set<DocumentReference> rootGroups) throws GroupException
    {
        Collection<String> cacheWikis = getSearchWikis(reference, wikiTarget, false);

        // Try in the cache
        GroupCacheEntry entry = this.memberGroupsCache.getCacheEntry(reference, cacheWikis, true);

        Collection<DocumentReference> groups = get(entry, recurse);
        if (groups != null) {
            if (rootGroups != null) {
                rootGroups.addAll(groups);
            }

            return groups;
        }

        // Not in the cache

        synchronized (entry) {
            // Check if it was calculated by another thread in the meantime
            groups = get(entry, recurse);
            if (groups != null) {
                if (rootGroups != null) {
                    rootGroups.addAll(groups);
                }

                return groups;
            }

            // Get direct groups
            groups = entry.getDirect();
            if (groups == null) {
                groups = entry.setDirect(getGroups(reference, cacheWikis));
            }

            // Get all groups
            if (recurse) {
                Set<DocumentReference> resolvedGroups = new LinkedHashSet<>();

                if (rootGroups == null) {
                    rootGroups = resolvedGroups;
                }

                // Make sure asked reference won't be resolved
                resolvedGroups.add(reference);

                // Recursively resolve sub-groups
                for (DocumentReference group : groups) {
                    // Protect against cross references between groups
                    if (!rootGroups.contains(group)) {
                        rootGroups.add(group);
                        resolvedGroups.add(group);

                        Collection<DocumentReference> subGRoups = getGroups(group, cacheWikis, true, rootGroups);

                        if (subGRoups != null) {
                            resolvedGroups.addAll(subGRoups);
                        } else {
                            groups = null;
                        }
                    } else {
                        groups = null;
                    }
                }

                // Remove asked reference from groups
                resolvedGroups.remove(reference);

                if (groups != null || rootGroups == resolvedGroups) {
                    groups = entry.setAll(resolvedGroups);
                }
            } else {
                if (rootGroups != null) {
                    rootGroups.addAll(groups);
                }
            }

            return groups;
        }
    }

    private XWikiGroupService getXWikiGroupService(XWikiContext xcontext) throws GroupException
    {
        try {
            return xcontext.getWiki().getGroupService(xcontext);
        } catch (XWikiException e) {
            throw new GroupException("Failed to get group service", e);
        }
    }

    private Collection<DocumentReference> getGroups(DocumentReference reference, Object wikiTarget)
        throws GroupException
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        XWikiGroupService groupService = getXWikiGroupService(xcontext);

        Collection<String> searchWikis = getSearchWikis(reference, wikiTarget, true);

        Set<DocumentReference> groups = new LinkedHashSet<>();

        WikiReference currrentWiki = xcontext.getWikiReference();
        for (String wiki : searchWikis) {
            try {
                xcontext.setWikiId(wiki);

                for (DocumentReference groupReference : groupService.getAllGroupsReferencesForMember(reference, -1, 0,
                    xcontext)) {
                    groups.add(this.referenceFactory.getReference(groupReference));
                }
            } catch (XWikiException e) {
                this.logger.error("Failed to get all groups for member [{}] in wiki [{}]", reference, wiki, e);
            } finally {
                xcontext.setWikiReference(currrentWiki);
            }
        }

        return groups;
    }

    private Collection<DocumentReference> get(GroupCacheEntry entry, boolean recurse)
    {
        Collection<DocumentReference> references;

        if (recurse) {
            references = entry.getAll();
        } else {
            references = entry.getDirect();
        }

        return references;
    }

    @Override
    public Set<DocumentReference> getGroups(WikiReference wikiReference) throws GroupException
    {
        Set<DocumentReference> groups = this.wikiGroupCache.get(wikiReference.getName());

        if (groups == null) {
            synchronized (this.wikiGroupCache) {
                groups = this.wikiGroupCache.get(wikiReference.getName());

                if (groups == null) {
                    XWikiContext xcontext = this.xcontextProvider.get();

                    XWikiGroupService groupService = getXWikiGroupService(xcontext);

                    WikiReference currentWiki = xcontext.getWikiReference();
                    try {
                        xcontext.setWikiReference(wikiReference);

                        List<String> groupNames =
                            (List<String>) groupService.getAllMatchedGroups(null, false, 0, 0, null, xcontext);

                        groups = groupNames.stream()
                            .map(groupName -> this.referenceFactory
                                .getReference(this.resolver.resolve(groupName, wikiReference)))
                            .collect(Collectors.toSet());
                    } catch (XWikiException e) {
                        throw new GroupException("Failed to get groups for wiki [" + wikiReference.getName() + "]", e);
                    } finally {
                        xcontext.setWikiReference(currentWiki);
                    }

                    this.wikiGroupCache.set(wikiReference.getName(), groups);
                }
            }
        }

        return groups;
    }

    @Override
    public boolean isGroup(DocumentReference reference) throws GroupException
    {
        // Get all the groups of the wiki
        Set<DocumentReference> wikiGroup = getGroups(reference.getWikiReference());

        // Check if the reference is part of those groups
        return wikiGroup.contains(reference);
    }

    @Override
    public Collection<DocumentReference> getMembers(DocumentReference reference, boolean recurse) throws GroupException
    {
        return getMembers(reference, recurse, null);
    }

    private Collection<DocumentReference> getMembers(DocumentReference reference, boolean recurse,
        Set<DocumentReference> rootMembers) throws GroupException
    {
        // Make sure the reference is a group
        if (!isGroup(reference)) {
            return Collections.emptyList();
        }

        // Try in the cache
        GroupCacheEntry entry = this.groupMembersCache.getCacheEntry(reference, true);

        Collection<DocumentReference> members = get(entry, recurse);
        if (members != null) {
            if (rootMembers != null) {
                rootMembers.addAll(members);
            }

            return members;
        }

        // Not in the cache

        synchronized (entry) {
            // Check if it was calculated by another thread in the meantime
            members = get(entry, recurse);
            if (members != null) {
                if (rootMembers != null) {
                    rootMembers.addAll(members);
                }

                return members;
            }

            // Get direct members
            members = entry.getDirect();
            if (members == null) {
                members = entry.setDirect(getMembers(reference));
            }

            // Get all members
            if (recurse) {
                Set<DocumentReference> resolvedMembers = new LinkedHashSet<>();

                if (rootMembers == null) {
                    rootMembers = resolvedMembers;
                }

                // Make sure asked reference won't be resolved
                resolvedMembers.add(reference);

                // Recursively resolve sub-groups
                for (DocumentReference member : members) {
                    // Protect against cross references between groups
                    if (!rootMembers.contains(member)) {
                        rootMembers.add(member);
                        resolvedMembers.add(member);

                        Collection<DocumentReference> subMembers = getMembers(member, true, rootMembers);

                        if (subMembers != null) {
                            resolvedMembers.addAll(subMembers);
                        } else {
                            members = null;
                        }
                    } else {
                        members = null;
                    }
                }

                // Remove asked reference from members
                resolvedMembers.remove(reference);

                if (members != null || rootMembers == resolvedMembers) {
                    members = entry.setAll(resolvedMembers);
                }
            } else {
                if (rootMembers != null) {
                    rootMembers.addAll(members);
                }
            }

            return members;
        }
    }

    private Collection<DocumentReference> getMembers(DocumentReference reference) throws GroupException
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        XWikiGroupService groupService = getXWikiGroupService(xcontext);

        Collection<String> memberStrings;
        try {
            memberStrings =
                groupService.getAllMembersNamesForGroup(this.serializer.serialize(reference), -1, 0, xcontext);
        } catch (XWikiException e) {
            throw new GroupException("Failed to get members of group [" + reference + "]", e);
        }

        Set<DocumentReference> members = new LinkedHashSet<>();

        for (String memberString : memberStrings) {
            members.add(this.referenceFactory.getReference(this.resolver.resolve(memberString, reference)));
        }

        return members;
    }
}

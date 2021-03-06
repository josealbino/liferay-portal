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

package com.liferay.portal.security.membershippolicy.bundle.usergroupmembershippolicyfactoryimpl;

import com.liferay.portal.kernel.security.membershippolicy.UserGroupMembershipPolicy;
import com.liferay.portal.kernel.util.StackTraceUtil;
import com.liferay.portal.model.UserGroup;

import java.io.Serializable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Peter Fellwock
 */
@Component(
	immediate = true,
	property = {"service.ranking:Integer=" + Integer.MAX_VALUE}
)
public class TestUserGroupMembershipPolicy
	implements UserGroupMembershipPolicy {

	@Override
	public void checkMembership(
		long[] userIds, long[] addUserGroupIds, long[] removeUserGroupIds) {

		_atomicReference.set(StackTraceUtil.getCallerKey());
	}

	@Override
	public boolean isMembershipAllowed(long userId, long userGroupId) {
		if (userId == 1) {
			return true;
		}

		return false;
	}

	@Override
	public boolean isMembershipRequired(long userId, long userGroupId) {
		if (userId == 1) {
			return true;
		}

		return false;
	}

	@Override
	public void propagateMembership(
		long[] userIds, long[] addUserGroupIds, long[] removeUserGroupIds) {

		_atomicReference.set(StackTraceUtil.getCallerKey());
	}

	@Override
	public void verifyPolicy() {
		_atomicReference.set(StackTraceUtil.getCallerKey());
	}

	@Override
	public void verifyPolicy(UserGroup userGroup) {
		_atomicReference.set(StackTraceUtil.getCallerKey());
	}

	@Override
	public void verifyPolicy(
		UserGroup userGroup, UserGroup oldUserGroup,
		Map<String, Serializable> oldExpandoAttributes) {

		_atomicReference.set(StackTraceUtil.getCallerKey());
	}

	@Reference(target = "(test=AtomicState)")
	protected void setAtomicReference(AtomicReference<String> atomicReference) {
		_atomicReference = atomicReference;
	}

	private AtomicReference<String> _atomicReference;

}
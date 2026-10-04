package com.okcrm.modules.customer.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.customer.domain.Contact;
import com.okcrm.modules.customer.infra.mapper.ContactMapper;
import com.okcrm.modules.customer.internal.dto.ContactResponse;
import com.okcrm.modules.customer.internal.dto.ContactSaveRequest;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客户联系人服务。
 */
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactMapper contactMapper;
    private final CustomerService customerService;

    public List<ContactResponse> listByCustomer(Long customerId) {
        // 复用客户详情做可见性校验，避免「看不到客户却能读到它的联系人」
        customerService.detail(customerId);
        return customerService.contactsOf(customerId).stream()
                .map(ContactResponse::from)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ContactResponse create(Long customerId, ContactSaveRequest request) {
        customerService.detail(customerId);

        Contact contact = new Contact();
        contact.setCustomerId(customerId);
        applyFields(contact, request);
        contactMapper.insert(contact);
        return ContactResponse.from(contact);
    }

    @Transactional(rollbackFor = Exception.class)
    public ContactResponse update(Long contactId, ContactSaveRequest request) {
        Contact existing = require(contactId);
        customerService.detail(existing.getCustomerId());

        Contact update = new Contact();
        update.setId(contactId);
        applyFields(update, request);
        contactMapper.updateById(update);

        return ContactResponse.from(contactMapper.selectById(contactId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long contactId) {
        Contact existing = require(contactId);
        customerService.detail(existing.getCustomerId());
        contactMapper.deleteById(contactId);
    }

    private void applyFields(Contact contact, ContactSaveRequest request) {
        contact.setName(request.name());
        contact.setPosition(request.position());
        contact.setPhone(request.phone());
        contact.setEmail(request.email());
        contact.setPrimaryContact(Boolean.TRUE.equals(request.primaryContact()));
        contact.setRemark(request.remark());
    }

    private Contact require(Long contactId) {
        Contact contact = contactId == null ? null : contactMapper.selectById(contactId);
        if (contact == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "联系人不存在");
        }
        return contact;
    }
}

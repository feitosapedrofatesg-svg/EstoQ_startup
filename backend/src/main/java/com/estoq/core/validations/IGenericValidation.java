package com.estoq.core.validations;

import com.estoq.core.domains.BaseModel;

public interface IGenericValidation<E extends BaseModel> {

    void validate(E entity);
}
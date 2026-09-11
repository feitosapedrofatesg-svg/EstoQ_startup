package com.estoq.core.services;

import com.estoq.core.domains.BaseModel;
import com.estoq.core.dtos.BaseDTO;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.helpers.IGenericAdapter;
import com.estoq.core.repositories.IGenericRepository;
import com.estoq.core.validations.IGenericValidation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** CRUD e conversão dentro da transação: nenhum proxy JPA escapa para o controller. */
@Transactional(readOnly = true)
public abstract class GenericService<E extends BaseModel, D extends BaseDTO> implements IGenericService<D> {

    protected final IGenericRepository<E> repository;
    protected final IGenericAdapter<E, D> adapter;
    protected final IGenericValidation<E> validation;

    protected GenericService(IGenericRepository<E> repository, IGenericAdapter<E, D> adapter,
            IGenericValidation<E> validation) {
        this.repository = repository;
        this.adapter = adapter;
        this.validation = validation;
    }

    protected E findActive(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new BusinessException("Registro não encontrado ou inativo.", HttpStatus.NOT_FOUND));
    }

    public D buscar(Long id) {
        return adapter.toDto(findActive(id));
    }

    public Page<D> listar(Pageable pageable) {
        return repository.findAllByAtivoTrue(pageable).map(adapter::toDto);
    }

    @Transactional
    public D criar(D dto) {
        if (dto.getId() != null) {
            throw new FieldValidationException("id", "Não informe id na criação.");
        }
        E entity = adapter.toEntity(dto);
        beforeSave(entity, dto);
        validation.validate(entity);
        repository.saveAndFlush(entity);
        afterSave(entity);
        return adapter.toDto(entity);
    }

    @Transactional
    public D atualizar(Long id, D dto) {
        E entity = findActive(id);
        if (dto.getVersion() == null) {
            throw new FieldValidationException("version", "Informe a versão consultada para atualizar.");
        }
        if (!Objects.equals(dto.getVersion(), entity.getVersion())) {
            throw new ConflictException();
        }
        adapter.updateEntity(dto, entity);
        beforeSave(entity, dto);
        validation.validate(entity);
        repository.flush();
        afterSave(entity);
        return adapter.toDto(entity);
    }

    @Transactional
    public void excluir(Long id) {
        E entity = findActive(id);
        beforeDelete(entity);
        entity.setAtivo(false);
        repository.flush();
        afterSave(entity);
    }

    protected void beforeSave(E entity, D dto) {
    }

    protected void afterSave(E entity) {
    }

    protected void beforeDelete(E entity) {
    }
}
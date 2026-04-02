/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.repository.jpa;

import java.util.Collection;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA implementation of the {@link OwnerRepository} interface.
 *
 * @author Mike Keith
 * @author Rod Johnson
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Vitaliy Fedoriv
 */
@Repository
@Profile("jpa")
public class JpaOwnerRepositoryImpl implements OwnerRepository {

    @PersistenceContext
    private EntityManager em;


    /**
     * Important: owner REST responses serialize pets, pet types and pet visits. Fetching them here keeps the
     * web layer independent from an open persistence session.
     */
    @SuppressWarnings("unchecked")
    public Collection<Owner> findByLastName(String lastName) {
        Query query = this.em.createQuery(
            "SELECT DISTINCT owner FROM Owner owner "
                + "left join fetch owner.pets pet "
                + "left join fetch pet.type "
                + "left join fetch pet.visits "
                + "WHERE owner.lastName LIKE :lastName");
        query.setParameter("lastName", lastName + "%");
        return query.getResultList();
    }

    @Override
    public Owner findById(int id) {
        Query query = this.em.createQuery(
            "SELECT DISTINCT owner FROM Owner owner "
                + "left join fetch owner.pets pet "
                + "left join fetch pet.type "
                + "left join fetch pet.visits "
                + "WHERE owner.id = :id");
        query.setParameter("id", id);
        return (Owner) query.getSingleResult();
    }


    @Override
    public void save(Owner owner) {
        if (owner.getId() == null) {
            this.em.persist(owner);
        } else {
            this.em.merge(owner);
        }

    }

	@SuppressWarnings("unchecked")
	@Override
	public Collection<Owner> findAll() throws DataAccessException {
		Query query = this.em.createQuery(
            "SELECT DISTINCT owner FROM Owner owner "
                + "left join fetch owner.pets pet "
                + "left join fetch pet.type "
                + "left join fetch pet.visits");
        return query.getResultList();
	}

	@Override
	public void delete(Owner owner) throws DataAccessException {
		this.em.remove(this.em.contains(owner) ? owner : this.em.merge(owner));
	}

}

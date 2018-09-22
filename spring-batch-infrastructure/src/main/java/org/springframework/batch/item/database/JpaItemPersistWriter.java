package org.springframework.batch.item.database;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.util.Assert;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.List;

/**
 * {@link org.springframework.batch.item.database.JpaItemWriter} that is using a JPA
 * EntityManagerFactory to persist or merge any Entities that aren't part of the
 * persistence context.
 *
 * @author jojoldu
 *
 */
public class JpaItemPersistWriter<T> extends JpaItemWriter<T> {
    protected static final Log logger = LogFactory.getLog(JpaItemPersistWriter.class);
    private Class<T> domainClass;

    public JpaItemPersistWriter() {}

    public JpaItemPersistWriter(Class<T> domainClass, EntityManagerFactory entityManager) {
        this.domainClass = domainClass;
        super.entityManagerFactory = entityManager;
    }

    public void setDomainClass(Class<T> domainClass) {
        Assert.notNull(domainClass, "domainClass must not be null!");
        this.domainClass = domainClass;
    }

    /**
     * Merge all provided items that aren't already in the persistence context
     * and then flush the entity manager.
     *
     * @see org.springframework.batch.item.ItemWriter#write(List)
     */
    @Override
    public void write(List<? extends T> items) {
        EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        if (entityManager == null) {
            throw new DataAccessResourceFailureException("Unable to obtain a transactional EntityManager");
        }
        JpaEntityInformation<T, ?> entityInformation = JpaEntityInformationSupport.getEntityInformation(domainClass, entityManager);
        doWrite(entityInformation, entityManager, items);
        entityManager.flush();
    }

    /**
     * Do perform the actual write operation. This can be overridden in a
     * subclass if necessary.
     *
     * Entity persist or merge
     *
     * @param entityInformation the entityInformation to use for the check new entity
     * @param entityManager     the EntityManager to use for the operation
     * @param items             the list of items to use for the write
     */
    protected void doWrite(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager, List<? extends T> items) {

        if (logger.isDebugEnabled()) {
            logger.debug("Writing to JPA with " + items.size() + " items.");
        }

        long mergeCount = 0;
        long persistCount = 0;

        for (T item : items) {
            if (!entityManager.contains(item)) {
                if (entityInformation.isNew(item)) {
                    entityManager.persist(item);
                    persistCount++;
                } else {
                    entityManager.merge(item);
                    mergeCount++;
                }
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug(mergeCount + " entities merged.");
            logger.debug(persistCount + " entities persisted.");
            logger.debug((items.size() - mergeCount - persistCount) + " entities found in persistence context.");
        }
    }
}

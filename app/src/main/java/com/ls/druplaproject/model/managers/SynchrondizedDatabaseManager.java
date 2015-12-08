package com.ls.druplaproject.model.managers;

import com.ls.druplaproject.model.data.base.DatabaseFacade;
import com.ls.http.base.client.LSClient;

/**
 * Created on 25.05.2015. Use for database-based storage only
 */
public abstract class SynchrondizedDatabaseManager<ClassToManage,ParametersClass,TagClass> extends BaseItemManager<ClassToManage, ParametersClass,TagClass> {

    protected SynchrondizedDatabaseManager(LSClient client) {
        super(client);
    }

    @Override
    protected final boolean storeResponse(ClassToManage response, TagClass tag) {
        DatabaseFacade facade = DatabaseFacade.instance();
        synchronized (facade) {
            try {
                facade.open();
                facade.beginTransactions();
                boolean result = synchronizedStoreResponse(response, tag);
                if (result) {
                    facade.setTransactionSuccesfull();
                }
                return result;
            } finally {
                facade.endTransactions();
                facade.close();
            }
        }
    }

    @Override
    protected ClassToManage restoreResponse(TagClass tag) {
        DatabaseFacade facade = DatabaseFacade.instance();
        synchronized (facade) {
            try {
                facade.open();
                ClassToManage result = synchronizeddRetoreResponse(tag);
                return result;
            } finally {
                facade.close();
            }
        }
    }

    protected abstract boolean synchronizedStoreResponse(ClassToManage response, TagClass tag);

    protected abstract ClassToManage synchronizeddRetoreResponse(TagClass tag);
}

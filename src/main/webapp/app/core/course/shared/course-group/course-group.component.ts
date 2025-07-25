import { Component, EventEmitter, Input, OnDestroy, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { Observable, Subject, of } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { Course, CourseGroup } from 'app/core/course/shared/entities/course.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { iconsAsHTML } from 'app/shared/util/icons.utils';
import { download, generateCsv, mkConfig } from 'export-to-csv';
import { faDownload, faUserSlash } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { RouterLink } from '@angular/router';
import { addPublicFilePrefix } from 'app/app.constants';
import { UsersImportButtonComponent } from 'app/shared/user-import/button/users-import-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';

const cssClasses = {
    alreadyMember: 'already-member',
    newlyAddedMember: 'newly-added-member',
};

export type GroupUserInformationRow = {
    [NAME_KEY]: string;
    [USERNAME_KEY]: string;
    [EMAIL_KEY]: string;
    [REGISTRATION_NUMBER_KEY]: string;
};

@Component({
    selector: 'jhi-course-group',
    templateUrl: './course-group.component.html',
    styleUrls: ['./course-group.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [UsersImportButtonComponent, FaIconComponent, TranslateDirective, DataTableComponent, NgxDatatableModule, RouterLink, ProfilePictureComponent, DeleteButtonDirective],
})
export class CourseGroupComponent implements OnDestroy {
    @ViewChild(DataTableComponent) dataTable: DataTableComponent;

    @Input() allGroupUsers: User[] = [];
    @Input() isLoadingAllGroupUsers = false;
    @Input() isAdmin = false;
    @Input() course: Course;
    @Input() tutorialGroup: TutorialGroup | undefined = undefined;
    @Input() courseGroup: CourseGroup;
    @Input() exportFileName: string;

    @Input() userSearch: (loginOrName: string) => Observable<HttpResponse<User[]>> = () => of(new HttpResponse<User[]>({ body: [] }));
    @Input() addUserToGroup: (login: string) => Observable<HttpResponse<void>> = () => of(new HttpResponse<void>());
    @Input() removeUserFromGroup: (login: string) => Observable<HttpResponse<void>> = () => of(new HttpResponse<void>());
    @Input() handleUsersSizeChange: (filteredUsersSize: number) => void = () => {};

    @Output() importFinish: EventEmitter<void> = new EventEmitter();

    readonly ActionType = ActionType;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass: string;

    // Icons
    faDownload = faDownload;
    faUserSlash = faUserSlash;

    /**
     * Unsubscribe dialog error source on component destruction.
     */
    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Receives the search text and filter results from DataTableComponent, modifies them and returns the result which will be used by ngbTypeahead.
     *
     * 1. Perform server-side search using the search text
     * 2. Return results from server query that contain all users (instead of only the client-side users who are group members already)
     *
     * @param stream$ stream of searches of the format {text, entities} where entities are the results
     * @return stream of users for the autocomplete
     */
    searchAllUsers = (stream$: Observable<{ text: string; entities: User[] }>): Observable<User[]> => {
        return stream$.pipe(
            switchMap(({ text: loginOrName }) => {
                this.searchFailed = false;
                this.searchNoResults = false;
                if (loginOrName.length < 3) {
                    return of([]);
                }
                this.isSearching = true;
                return this.userSearch(loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        tap((users) => {
                            if (users.length === 0) {
                                this.searchNoResults = true;
                            }
                        }),
                        catchError(() => {
                            this.searchFailed = true;
                            return of([]);
                        }),
                    );
            }),
            tap(() => {
                this.isSearching = false;
            }),
            tap((users) => {
                setTimeout(() => {
                    for (let i = 0; i < this.dataTable.typeaheadButtons.length; i++) {
                        const button = this.dataTable.typeaheadButtons[i];
                        const isAlreadyInGroup = this.allGroupUsers.map((user) => user.id).includes(users[i].id);
                        const hasIcon = button.querySelector('fa-icon');
                        if (!hasIcon) {
                            button.insertAdjacentHTML('beforeend', iconsAsHTML[isAlreadyInGroup ? 'users' : 'users-plus']);
                        }
                        if (isAlreadyInGroup) {
                            button.classList.add(cssClasses.alreadyMember);
                        }
                    }
                });
            }),
        );
    };

    /**
     * Receives the user that was selected in the autocomplete and the callback from DataTableComponent.
     * The callback inserts the search term of the selected entity into the search field and updates the displayed users.
     *
     * @param user The selected user from the autocomplete suggestions
     * @param callback Function that can be called with the selected user to trigger the DataTableComponent default behavior
     */
    onAutocompleteSelect = (user: User, callback: (user: User) => void): void => {
        // If the user is not part of this course group yet, perform the server call to add them
        if (!this.allGroupUsers.map((u) => u.id).includes(user.id) && user.login) {
            this.isTransitioning = true;
            this.addUserToGroup(user.login).subscribe({
                next: () => {
                    this.isTransitioning = false;

                    // Add newly added user to the list of all users in the course group
                    this.allGroupUsers.push(user);

                    // Hand back over to the data table for updating
                    callback(user);

                    // Flash green background color to signal to the user that this record was added
                    this.flashRowClass(cssClasses.newlyAddedMember);
                },
                error: () => {
                    this.isTransitioning = false;
                },
            });
        } else {
            // Hand back over to the data table
            callback(user);
        }
    };

    /**
     * Remove user from course group
     *
     * @param user User that should be removed from the currently viewed course group
     */
    removeFromGroup(user: User) {
        if (user.login) {
            this.removeUserFromGroup(user.login).subscribe({
                next: () => {
                    this.allGroupUsers = this.allGroupUsers.filter((u) => u.login !== user.login);
                    this.dialogErrorSource.next('');
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
        }
    }

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param user
     */
    searchResultFormatter = (user: User) => {
        const { name, login } = user;
        return `${name} (${login})`;
    };

    /**
     * Converts a user object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param user User
     */
    searchTextFromUser = (user: User): string => {
        return user.login || '';
    };

    /**
     * Computes the row class that is being added to all rows of the datatable
     */
    dataTableRowClass = () => {
        return this.rowClass;
    };

    /**
     * Can be used to highlight rows temporarily by flashing a certain css class
     *
     * @param className Name of the class to be applied to all rows
     */
    flashRowClass = (className: string) => {
        this.rowClass = className;
        setTimeout(() => (this.rowClass = ''));
    };

    /**
     * Method for exporting the csv with the needed data
     */
    exportUserInformation = () => {
        if (this.allGroupUsers.length > 0) {
            const rows: any[] = this.allGroupUsers.map((user: User): GroupUserInformationRow => {
                return {
                    [NAME_KEY]: user.name?.trim() ?? '',
                    [USERNAME_KEY]: user.login?.trim() ?? '',
                    [EMAIL_KEY]: user.email?.trim() ?? '',
                    [REGISTRATION_NUMBER_KEY]: user.visibleRegistrationNumber?.trim() ?? '',
                };
            });
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
            this.exportAsCsv(rows, keys);
        }
    };

    /**
     * Method for generating the csv file containing the user information
     *
     * @param rows the data to export
     * @param keys the keys of the data
     */
    exportAsCsv = (rows: any[], keys: string[]) => {
        const options = {
            fieldSeparator: ';',
            quoteStrings: true,
            quoteCharacter: '"',
            showLabels: true,
            showTitle: false,
            filename: this.exportFileName,
            useTextFile: false,
            useBom: true,
            columnHeaders: keys,
        };
        const csvExportConfig = mkConfig(options);
        const csvData = generateCsv(csvExportConfig)(rows);
        download(csvExportConfig)(csvData);
    };
    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}

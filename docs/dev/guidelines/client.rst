******
Client
******

0. General
==========

The Artemis client is an Angular project. Keep https://angular.io/guide/styleguide in mind.

Some general aspects:

* The Artemis client uses lazy loading to keep the initial bundle size as small as possible.
* Code quality and test coverage are important. Try to reuse code and avoid code duplication. Write meaningful tests!

**Angular Migration**

* Use **standalone components** instead of Angular modules: https://angular.dev/reference/migrations/standalone
* Use the new ``signals`` to granular track how and where state is used throughout an application, allowing Angular to optimize rendering updates: https://angular.dev/guide/signals
* Find out more in the following guide: https://blog.angular-university.io/angular-signal-components
* Use the new ``input()`` and ``output()`` decorators instead of ``@Input()`` and ``@Output()``.

    .. code-block:: ts

        // Don't
        @Input() myInput: string;
        @Output() myOutput = new EventEmitter<string>();

        // Do
        myInput = input<string>();
        myOutput = output<string>();

* Use the new ``inject`` function, because it offers more accurate types and better compatibility with standard decorators, compared to constructor-based injection: https://angular.dev/reference/migrations/inject-function
* Use the new way of defining queries for ``viewChild()``, ``contentChild()``, ``viewChildren()``, ``contentChildren()``: https://ngxtension.netlify.app/utilities/migrations/queries-migration
* You can find an example for a migration in the pull request `9299 <https://github.com/ls1intum/Artemis/pull/9299>`_.
* Use ``OnPush`` change detection strategy for components whenever possible: https://blog.angular-university.io/onpush-change-detection-how-it-works

.. WARNING::
    **Never invoke methods from the html template. The automatic change tracking in Angular will kill the application performance!**

    This also includes getter functions. The only exception is the use of `signals <https://angular.io/guide/signals>`_.

    If you need more information/examples or methods to avoid function calls, have a look at this `article <https://dev.to/sandrocagara/angular-avoid-function-calls-in-templates-1mfa>`_.

1. Names
========

1. Use PascalCase for type names.
2. Do not use "I" as a prefix for interface names.
3. Use PascalCase for enum values.
4. Use camelCase for function names.
5. Use camelCase for property names and local variables.
6. Use SCREAMING_SNAKE_CASE for constants, i.e. properties with the ``readonly`` keyword.
7. Do not use "_" as a prefix for private properties.
8. Use whole words in names when possible.

2. Components
=============

In our project, we promote the creation of standalone components instead of using Angular modules. A standalone component is a self-contained unit that encapsulates its own logic, view, and styles. It doesn't directly depend on its parent or child components and can be reused in different parts of the application.
For existing components that are not standalone, we should aim to migrate them step by step. This migration process should be done gradually and carefully, to avoid introducing bugs. It's recommended to thoroughly test the component after each change to ensure it still works as expected.
Standalone components can be generated with the Angular CLI using ``ng g c <component-name> --standalone``.

More info about standalone components: https://angular.dev/guide/components/importing#standalone-components

1. 1 file per logical component (e.g. parser, scanner, emitter, checker).
2. files with ".generated.*" suffix are auto-generated, do not hand-edit them.

3. Types
========

1. Do not export types/functions unless you need to share it across multiple components.
2. Do not introduce new types/values to the global namespace.
3. Shared types/interfaces should be defined in 'types.ts'.
4. Within a file, type definitions should come first.
5. Interfaces and types offer almost the same functionality. To ensure consistency, choose ``interface`` over ``type`` whenever possible.

    .. code-block:: ts

        // Dont do
        type AngularLink = {
            text: string;
            routerLink: (string | number) [];
        }

        // Do
        interface AngularLink {
            text: string;
            routerLink: (string | number) [];
        }

        // And this is also allowed (because interface is not possible here)
        type RouterLinkPart = string | number;



6. Use strict typing to avoid type errors: **Never** use ``any``.

7. Do not use anonymous data structures.

    .. code-block:: ts

        interface AngularLink {
            text: string;
            routerLink: (string | number) [];
        }

        // Do not do this because the type error will not be recognized during compile time.
        const link = { text: 'I am a Link', routerLink: 4 } as AngularLink;

        // Instead do this (it will throw a type error during compilation because '4' is not an array of strings)
        const link: AngularLink = { text: 'I am a Link', routerLink: '4' };

4. ``null`` and ``undefined``
=============================

Use **undefined**. **Never** use ``null``.

5. General Assumptions
======================

1. Consider objects like Nodes, Symbols, etc. as immutable outside the component that created them. Do not change them.
2. Consider arrays as immutable by default after creation.

6. Comments
============

Use JSDoc style comments for functions, interfaces, enums, and classes.
Provide extensive documentation inline and using JSDoc to make sure other developers can understand the code and the rationale behind the implementation
without having to read the code.

7. Strings
============

1. Use single quotes for strings.
2. All strings visible to the user need to be localized (see next chapter)

8. Localization
===============

1. Make an entry in the corresponding ``i18n/{language}/{area}.json`` files for all languages Artemis supports (currently English and German).
2. To display the string in HTML files, use the ``jhiTranslate`` directive or the ``artemisTranslate`` pipe.
3. To ensure consistency, always choose the directive over the pipe whenever possible.

Do:

.. code-block:: html+ng2

    <span jhiTranslate="global.title"></span>

    <!-- ok, because there is other content in the span as well -->
    <span>
        {{ 'global.title' | artemisTranslate }}
        <fa-icon [icon]="faDelete" />
    </span>

Don't do:

.. code-block:: html+ng2

    <!-- use the directive instead -->
    <span>{{ 'global.title' | artemisTranslate }}</span>

    <!-- Do not add the translated text between the HTML tags -->
    <span jhiTranslate="global.title">Artemis</span>

9. Buttons and Links
====================

1. Be aware that Buttons navigate only in the same tab while Links provide the option to use the context menu or a middle-click to open the page in a new tab. Therefore:
2. Buttons are best used to trigger certain functionalities (e.g. ``<button (click)='deleteExercise(exercise)'>...</button``)
3. Links are best for navigating on Artemis (e.g. ``<a [routerLink]='getLinkForExerciseEditor(exercise)' [queryParams]='getQueryParamsForEditor(exercise)'>...</a>``)

10. Icons with Text
====================

If you use icons next to text (for example for a button or link), make sure that they are separated by a newline. HTML renders one or multiple newlines as a space.

Do this:

.. code-block:: html+ng2

    <fa-icon [icon]="'times'"></fa-icon>
    <span>Text</span>

Don't do one of these or any other combination of whitespaces:

.. code-block:: html+ng2

    <fa-icon [icon]="'times'"></fa-icon><span>Text</span>

    <fa-icon [icon]="'times'"></fa-icon><span> Text</span>
    <fa-icon [icon]="'times'"></fa-icon> <span>Text</span>

    <fa-icon [icon]="'times'"></fa-icon>
    <span> Text</span>

Ignoring this will lead to inconsistent spacing between icons and text.

11. Labels
==========

Use labels to caption inputs like text fields and checkboxes.
Associated labels help screen readers to read out the text of the label when the input is focused.
Additionally they allow the label to act as an input itself (e.g. the label also activates the checkbox).
Make sure to associate them by putting the input inside the label component or by adding the for attribute in the label referencing the id of the input.

Do one of these:

.. code-block:: html+ng2

    <!-- always prefer this solution -->
    <input id="inputId" class="form-check-input" type="checkbox" (click)="foo()" />
    <label class="form-check-label" for="inputId" jhiTranslate="artemisApp.labelText">
    </label>

    <!-- only do this if the first solution does not work -->
    <label class="form-check-label">
        <input class="form-check-input" type="checkbox" (click)="foo()" />
        {{ 'artemisApp.labelText' | artemisTranslate }}
    </label>


12. Code Style
==============

1. Use arrow functions over anonymous function expressions.
2. Always surround arrow function parameters.
    For example, ``x => x + x`` is wrong but the following are correct:

    1. ``(x) => x + x``
    2. ``(x,y) => x + y``
    3. ``<T>(x: T, y: T) => x === y``

3. Always surround loop and conditional bodies with curly braces. Statements on the same line are allowed to omit braces.
4. Open curly braces always go on the same line as whatever necessitates them.
5. Parenthesized constructs should have no surrounding whitespace.
    A single space follows commas, colons, and semicolons in those constructs. For example:

    1. ``for (var i = 0, n = str.length; i < 10; i++) { }``
    2. ``if (x < 10) { }``
    3. ``function f(x: number, y: string): void { }``

6. Use a single declaration per variable statement (i.e. use ``var x = 1; var y = 2;`` over ``var x = 1, y = 2;``).
7. ``else`` goes on the same line from the closing curly brace.
8. Use 4 spaces per indentation.

We use ``prettier`` to style code automatically and ``eslint`` to find additional issues.
You can find the corresponding commands to invoke those tools in ``package.json``.

13. Preventing Memory Leaks
===========================

It is crucial that you try to prevent memory leaks in both your components and your tests.

What are memory leaks?
**********************

A very good explanation that you should definitely read to understand the problem: https://auth0.com/blog/four-types-of-leaks-in-your-javascript-code-and-how-to-get-rid-of-them/

In essence:

*  JS is a garbage-collected language
*  Modern garbage collectors improve on this algorithm in different ways, but the essence is the same: **reachable pieces of memory are marked as such and the rest is considered garbage.**
*  Unwanted references are references to pieces of memory that the developer knows he or she won't be needing
   anymore but that for some reason are kept inside the tree of an active root. **In the context of JavaScript, unwanted references are variables kept somewhere in the code that will not be used anymore and point to a piece of memory that could otherwise be freed.**

What are common reasons for memory leaks?
*****************************************
https://auth0.com/blog/four-types-of-leaks-in-your-javascript-code-and-how-to-get-rid-of-them/:

*  Accidental global variables
*  Forgotten timers or callbacks
*  Out of DOM references
*  Closures

https://making.close.com/posts/finding-the-cause-of-a-memory-leak-in-jest
Mocks not being restored after the end of a test, especially when it involves global objects.

https://www.twilio.com/blog/prevent-memory-leaks-angular-observable-ngondestroy
RXJS subscriptions not being unsubscribed.

What are ways to identify memory leaks?
*****************************************
**Number 1:** Manually checking the heap usage and identifying heap dumps for causes of memory leaks
https://chanind.github.io/javascript/2019/10/12/jest-tests-memory-leak.html

Corresponding commands from the article for our project (enter in the root directory of the project):

.. code-block:: text

   node --expose-gc ./node_modules/.bin/jest --runInBand --logHeapUsage --config ./jest.config.js --env=jsdom

.. code-block:: text

   node --inspect-brk --expose-gc ./node_modules/.bin/jest --runInBand --logHeapUsage --config ./jest.config.js --env=jsdom

A live demonstration of this technique to find the reason for memory leaks in the repository: https://www.youtube.com/watch?v=GOYmouFrGrE

**Number 2:** Using the experimental leak detection feature from jest


.. code-block:: text

   --detectLeaks **EXPERIMENTAL**: Detect memory leaks in tests.
                                   After executing a test, it will try to garbage collect the global object used,
                                   and fail if it was leaked [boolean] [default: false]

  --runInBand, -i Run all tests serially in the current process
    (rather than creating a worker pool of child processes that run tests). This is sometimes useful for debugging, but such use cases are pretty rare.



Navigate into src/test/javascript and run either

.. code-block:: text

   jest --detectLeaks --runInBand

or

.. code-block:: text

   jest --detectLeaks


14. Defining Routes and Breadcrumbs
===================================

The ideal schema for routes is that every variable in a path is preceded by a unique path segment: ``\entityA\:entityIDA\entityB\:entityIDB``

For example, ``\courses\:courseId\:exerciseId`` is not a good path and should be written as ``\courses\:courseId\exercises\:exerciseId``.
Doubling textual segments like ``\lectures\statistics\:lectureId`` should be avoided and instead formulated as ``\lectures\:lectureId\statistics``.

When creating a completely new route you will have to register the new paths in ``navbar.ts``. A static/textual url segment gets a translation string assigned in the ``mapping`` table. Due to our code-style guidelines any ``-`` in the segment has to be replaced by a ``_``. If your path includes a variable, you will have to add the preceding path segment to the ``switch`` statement inside the ``addBreadcrumbForNumberSegment`` method.

.. code-block:: ts

    const mapping = {
        courses: 'artemisApp.course.home.title',
        lectures: 'artemisApp.lecture.home.title',
        // put your new directly translated url segments here
        // the index is the path segment in which '-' have to be replaced by '_'
        // the value is the translation string
        your_case: 'artemisApp.cases.title',
    };

    addBreadcrumbForNumberSegment(currentPath: string, segment: string): void {
        switch (this.lastRouteUrlSegment) {
            case 'course-management':
                // handles :courseId
                break;
            case 'lectures':
                // handles :lectureId
                break;
            case 'your-case':
                // add a case here for your :variable which is preceded in the path by 'your-case'
                break;
        }
    }

15. Strict Template Check
=========================

To prevent errors for strict template rule in TypeScript, Artemis uses following approaches.

Use ArtemisTranslatePipe instead of TranslatePipe
*************************************************
Do not use ``placeholder="{{ 'global.form.newpassword.placeholder' | translate }}"``

Use ``placeholder="{{ 'global.form.newpassword.placeholder' | artemisTranslate }}"``

Use ArtemisTimeAgoPipe instead of TimeAgoPipe
*********************************************
Do not use ``<span [ngbTooltip]="submittedDate | artemisDate">{{ submittedDate | amTimeAgo }}</span>``

Use ``<span [ngbTooltip]="submittedDate | artemisDate">{{ submittedDate | artemisTimeAgo }}</span>``

16. Chart Instantiation
=======================

We are using the framework `ngx-charts <https://github.com/swimlane/ngx-charts>`_ in order to instantiate charts and diagrams in Artemis.

The following is an example HTML template for a vertical bar chart:

.. code-block:: html+ng2

    <div #containerRef class="col-md-9">
        <ngx-charts-bar-vertical
            [view]="[containerRef.offsetWidth, 300]"
            [results]="ngxData"
            [scheme]="color"
            [legend]="false"
            [xAxis]="true"
            [yAxis]="true"
            [yScaleMax]="20"
            [roundEdges]="true"
            [showDataLabel]="true">
            <ng-template #tooltipTemplate let-model="model">
                {{ labelTitle }}: {{ round((model.value / totalValue) * 100, 1) }}%
            </ng-template>
        </ngx-charts-bar-vertical>
    </div>

Here are a few tips when using this framework:

    1. In order to configure the content of the tooltips in the chart, declare a `ng-template <https://angular.io/api/core/ng-template>`_ with the reference ``#tooltipTemplate``
       containing the desired content within the selector. The framework dynamically recognizes this template. In the example above,
       the tooltips are configured in order to present the percentage value corresponding to the absolute value represented by the bar.
       Depending on the chart type, there is more than one type of tooltip configurable.
       For more information visit https://swimlane.gitbook.io/ngx-charts/

    2. In order to manipulate the content of the data label (e.g. the text floating above a chart bar), the framework provides a ``[dataLabelFormatting]`` property in the
       HTML template that can be assigned to a method. For example:

       .. code-block:: html+ng2

          [dataLabelFormatting]="formatDataLabel"

       with

       .. code-block:: ts

          formatDataLabel(averageScore: number): string {
              return averageScore + '%';
          }

       appends a percentage sign to the data label.

       .. TIP::
           The method is passed to the framework itself and executed there. This means that at runtime it does not have access to global variables of the component it is originally implemented in.
           If this access is necessary, create a (readonly) variable assigned to this method and bind it to the component: ``readonly bindFormatting = this.formatDataLabel.bind(this);``

    3. Some design properties are not directly configurable via the framework (e.g. the font-size and weight of the data labels).
       The tool ``::ng-deep`` is useful in these situations as it allows to change some of these properties by overwriting them in
       a corresponding style sheet. Adapting the font-size and weight of data labels would look like this:

       .. WARNING::
           ``::ng-deep`` breaks the view encapsulation of the rule. This can lead to undesired and flaky side effects on other pages of Artemis.
           For more information, refer to the `Angular documentation <https://angular.io/guide/component-styles#deprecated-deep--and-ng-deep>`_.
           **Therefore, only use this annotation if this is absolutely necessary.** To limit the potential of side effects, add a ``:host`` in front of the command.

       .. code-block:: css

           :host::ng-deep .textDataLabel {
               font-weight: bolder;
               font-size: 15px !important;
           }

    4. In order to make the chart responsive in width, bind it to the width of its parent container.
       First, annotate the parent container with a reference (in the example ``#containerRef``).
       Then, when configuring the dimensions of the chart in ``[view]``, insert ``containerRef.offsetWidth`` instead
       of an specific value for the width.

    5. There are two ways to keep axis labels and axis ticks translation-sensitive if they contain natural language:

       * Axis labels are passed directly as property in the HTML template. Simply insert the translation string together with the translate pipe:

       .. code-block:: html+ng2

           [xAxisLabel]="'artemisApp.exam.charts.xAxisLabel' | artemisTranslate"
           [yAxisLabel]="'artemisApp.exam.charts.yAxisLabel' | artemisTranslate"

       * For some chart types, the framework derives the ticks of one axis from the name property of the passed data objects.
         So, these names have to be translated every time the user switches the language settings.
         In this case, inject the ``TranslateService`` to the underlying component and subscribe to the ``onLangChange`` event emitter:

       .. code-block:: ts

           constructor(private translateService: TranslateService) {
               this.translateService.onLangChange.subscribe(() => {
                   this.updateXAxisLabel(); // a method re-assigning the names of the objects to the translated string
               });
           }

Some parts of these guidelines are adapted from https://github.com/microsoft/TypeScript-wiki/blob/main/Coding-guidelines.md

17. Responsive Layout
=====================

Ensure that the layout of your page or component shrinks accordingly and adapts to all display sizes (responsive design).

Prefer using the ``.container`` class (https://getbootstrap.com/docs/5.2/layout/containers/) when you want to limit the page width on extra-large screens.
Do not use the following for this purpose if it can be avoided:

.. code-block:: html

    <div class="row justify-content-center">
        <div class="col-12 col-lg-8">
            <!-- Do not do this -->
        </div>
    </div>

18. WebSocket Subscriptions
===========================

The client must not subscribe to more than 20 WebSocket topics simultaneously, regardless of the amount of exercises, lectures, courses, etc. there are for one particular user.

Best Practices:

1. Dynamic Subscription Handling: Subscribe to topics on an as-needed basis. Unsubscribe from topics that are no longer needed to keep the number of active subscriptions within the recommended limit.
2. Efficient Topic Aggregation: Use topic aggregation techniques to consolidate related data streams into a single subscription wherever possible. Consequently, don't create a new topic if an existing one can be reused.
3. Small Messages: Send small messages and use DTOs. See :ref:`server-guideline-dto-usage` for more information and examples.

19. Styling
===========

We are using `Scss <https://sass-lang.com>`_ to write modular, reusable css. We have a couple of global scss files in ``webapp/content/scss``, but encourage component dependent css using `Angular styleUrls <https://angular.io/guide/component-styles>`_.

From a methodology viewpoint we encourage the use of `BEM <http://getbem.com/introduction/>`_:

.. code-block:: scss

    .my-container {
        // container styles
        &__content {
            // content styles
            &--modifier {
                // modifier styles
            }
        }
    }

Within the component html files, we encourage the use of `bootstrap css <https://getbootstrap.com/>`_:

.. code-block:: html

    <div class="d-flex ms-2">some content</div>


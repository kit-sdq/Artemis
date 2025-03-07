.. _modeling:

Modeling exercise
=================
.. contents:: Content of this document
    :local:
    :depth: 3

.. note::
   - Artemis uses an npm package called `Apollon`_ as its modeling editor.
   - It has its standalone version which can be accessed via https://apollon.ase.in.tum.de/.
   - The standalone version is free to use without the necessity of creating an account.
   - It offers additional features, including but not limited to, sharing and exporting the diagram.
   - For more information please visit `Apollon Standalone`_.


.. _Apollon: https://www.npmjs.com/package/@ls1intum/apollon
.. _Apollon Standalone: https://github.com/ls1intum/Apollon_standalone

Overview
--------

Conducting a Modeling exercise consists of 3 steps:

1. **Instructor prepares exercise:** Creates and configures the modeling exercise in Artemis.
2. **Student solves exercise:** Student works on the exercise and submits the solution.
3. **Tutors assesses submissions:** Reviews the submitted exercises and creates results for the students.

Setup
-----

The following sections describe the supported features and the process of creating a new modeling exercise.

- Open |course-management|.
- Navigate into **Exercises** of your preferred course.

    .. figure:: general/course-management-course-dashboard-exercises.png
              :align: center

Create new modeling exercise
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Click on **Create new modeling exercise**.

    .. figure:: modeling/create-new-modeling-exercise.png
              :align: center

The following screenshot illustrates the first section of the form. It consists of:

- **Title**: Title of an exercise.
- **Categories**: Category of an exercise.
- **Difficulty**: Difficulty of an exercise. (No level, Easy, Medium or Hard).
- **Mode**: Solving mode of an exercise. *This cannot be changed afterwards* (Individual or Team).
- **Release Date**: Date after which students can access the exercise.
- **Due Date**: Date till when students can work on the exercise.
- **Assessment Due Date**: Date after which students can view the feedback of the assessments from the instructors.
- **Inclusion in course score calculation**: Option that determines whether or not to include exercise in course score calculation.
- **Points**: Total points of an exercise.
- **Bonus Points**: Bonus points of an exercise.
- **Diagram Type**: Type of diagram that is used throughout an exercise.

    .. figure:: modeling/create-modeling-exercise-form-1.png
              :align: center

.. note::
   Fields marked with red are mandatory to be filled.

.. note::
   - The field **Diagram Type** determines the components that students/instructors can use while working on the exercise.
   - This option cannot be changed after creating the exercise.
   - For example: If the instructor selects class diagram as its diagram type, users (instructors/students) will now only be able to use components of class diagrams throughout the exercise.

    .. figure:: modeling/class-diagram-diagram-type.png
              :align: center

The following screenshot illustrates the second section of the form. It consists of:

- **Enable automatic assessment suggestions**: When enabled, Artemis tries to automatically suggest assessments for diagram elements based on previously graded submissions for this exercise.
- **Enable feedback suggestions from Athena**: When enabled, Artemis tries to automatically suggest assessments for diagram elements using the Athena service.
- **Problem Statement**: The task description of the exercise as seen by students.
- **Assessment Instructions**: Instructions for instructors while assessing the submission.

    .. figure:: modeling/create-modeling-exercise-form-2.png
              :align: center

.. note::
    If you are not clear about any of the fields, you can access additional hints by hovering over the |hint| icon for many of them.

    .. figure:: modeling/create-modeling-exercise-form-hint.png
              :align: center

The following screenshot illustrates the last section of the form. It consists of:

- **Example Solution**: Example solution of an exercise.
- **Example Solution Explanation**: Explanation of the example solution.
- **Example Solution Publication Date**: Date after which the example solution is accessible for students. If you leave this field empty, the solution will only be published to tutors.

    .. figure:: modeling/create-modeling-exercise-form-3.png
              :align: center


Once you are done defining the schema of an exercise, you can now create an exercise by clicking on |save| button.
You will then be redirected to **Example Submissions for Assessment Training** Page.

    .. figure:: modeling/example-submission-for-assessment-training.png
              :align: center

In this page, you can either *Create Example Submission* or *Use as Example Submission* for Assessment Training.
Example submissions can be used to assess the submissions of students semi-automatically.
Artemis uses those submissions to automatically apply the known assessment comments to similar model elements in other submissions as well.

- Select |create-example-submission| if you want to create an example submission from scratch.
- Alternatively, after the exercise already started, you can also use some submissions submitted by students as an example submission. For that, click on |use-as-example-submission|.


.. note::
    Artemis uses semi-automatic grading of modeling exercises using machine learning.
    You can hence train the model by selecting *Use in Assessment Training* checkbox while creating an example submission.

    .. figure:: modeling/use-in-assessment-training.png
              :align: center

Import Modeling Exercise
^^^^^^^^^^^^^^^^^^^^^^^^

- Alternatively, you can also import modeling exercise from the existing one by clicking on **Import Modeling Exercise**.

    .. figure:: modeling/import-modeling-exercise.png
              :align: center

- An import modal will prompt up, where you will have an option to select and import previous modeling exercises from the list by clicking on |import| button.

    .. figure:: modeling/import-modeling-exercise-modal.png
              :align: center

- Once you import one of the exercise, you will then be redirected to a form which is similar to *Create new modeling exercise* form with all the fields filled from imported exercise. You can now modify the fields as per your necessity to create a new Modeling Exercise.

Result
^^^^^^

    .. figure:: modeling/course-dashboard-exercise-modeling.png
              :align: center

- Click the |edit| button of the modeling exercise and adapt the interactive problem statement. There you can also set release and due dates.
- Click the |scores| button to see the scores achieved by the students.
- Click the |participation| button to see the list of students participated in the exercise.
- Click the |submission| button to see the list of submission submitted by students.
- Click the |example-submission| button to modify/add example submission of the exercise.
- Click the |delete| button to delete the exercise.
- You can get an overview of the exercise by clicking on the title.

Student Submission
------------------

- When the exercise is released students can work on the exercise.

    .. figure:: modeling/modeling-exercise-card-student-view.png
              :align: center

- They can start the exercise by clicking the |start| button.

- Once they start the exercise, they will now have the option to work on it in an online modeling editor by clicking on  the |open-modeling-editor| button.

- The screenshot below depicts the online modeling exercise interface for students. They can read the Problem Statement, work on the online editor and also provide an explanation to their solutions, if needed.

    .. figure:: modeling/modeling-exercise-students-interface.png
              :align: center

Assessment
----------

When the due date is over you can assess the submissions.

- To assess the submissions, first click on Assessment Dashboard.

    .. figure:: modeling/assessment-dashboard.png
              :align: center

- Then click on Submissions of the modeling exercise.

    .. figure:: modeling/exercise-dashboard.png
              :align: center

- You will then be redirected to *Submissions and Assessments* Page.

    .. figure:: modeling/submissions-dashboard.png
              :align: center

- Click on |assess-submission| button of specific student. You will then be redirected to the assessment page where you will be able to assess submission of that student.

- You can now start assessing the elements of the model by double clicking it. Once you double click, you will get an assessment dialog where you can assign points, feedback and navigate through all other assessable components.

    .. figure:: modeling/assessment-modal.png
              :align: center

- Alternatively, you can also assess the diagram by dragging and dropping assessment instructions from the *Assessment Instructions* section.

    .. figure:: modeling/assessment-instruction.png
              :align: center

- Feedback to the entire submission can also be added by clicking on the |add-new-feedback| button.

    .. figure:: general/feedback-modal.png
              :align: center

Once you're done assessing the solution, you can either:

- Click on |save| to save the incomplete assessment so that you can continue it afterwards.

- Click on |submit| to submit the assessment.

- Click on |cancel| to cancel and release the lock of the assessment.

- Click on |exercise-dashboard-button| to navigate to exercise dashboard page.

Automatic Assessment Suggestions
--------------------------------
If the checkbox ``Automatic assessment suggestions enabled`` is checked for a modeling exercise, Artemis generates assessment suggestions for submissions using the Athena service.
This section provides insights into how suggestions are retrieved in Artemis and how to apply them in the exercise grading process.

.. note::
   To learn how to set up an instance of the Athena service and configure your Artemis installation accordingly, please refer to the section :ref:`Athena Service <athena_service>`.

After clicking on |assess-submission| on one of the submission entries on the Submissions and Assessments Page, assessment suggestions are loaded automatically as indicated by the following loading indicator:

.. figure:: modeling/assessment-suggestions-loading-indicator.png
          :align: center
          :scale: 50%

Once assessment suggestions have been retrieved, a notice on top of the page indicates that the current submission contains assessment suggestions created via generative AI.

.. figure:: modeling/assessment-suggestions-notice.png
          :align: center

The suggestions themselves are shown as follows. If a suggestion directly references a diagram element, a dialog showing the suggested grading score for this specific suggestion as well as a suggestion on what could be improved is attached to the corresponding element.
In this example, a remark is made that an element is present in the evaluated BPMN diagram without being mentioned in the problem statement.

.. figure:: modeling/referenced-assessment-suggestion.png
          :align: center
          :scale: 50%

If a suggestion addresses a more general aspect of the diagram, multiple diagram elements at once, or elements that are missing from the diagram, the suggestion is shown in a card overview below the diagram.
These unreferenced suggestions can be accepted or discarded via buttons on the individual suggestion cards.

.. figure:: modeling/unreferenced-assessment-suggestion.png
          :align: center
          :scale: 50%

An demonstration of the automated generation of assessment suggestions for a business process model can be found in the following screencast:

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/47018?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Video tutorial of the automated assessment of modeling exercises on TUM-Live.
    </iframe>

To learn how automatic suggestions are generated and how exercises can be optimized for automatic evaluation, please refer to :ref:`Generation of Assessment Suggestions for Modeling Exercises<generation_of_assessment_suggestions_for_modeling_exercises>`.

Automatic Student Feedback
--------------------------

.. admonition:: Why Automatic Student Feedback
   :class: tip

   In large courses, providing timely and personalized feedback on modeling exercises is challenging. Automated student feedback helps learners identify misconceptions early, iterate on their work, and refine diagram modeling skills—all without waiting for an instructor or tutor to be available.

**Overview:**

When a modeling exercise is configured to allow ``Allow automatic AI preliminary feedback requests``, preliminary AI feedback can be requested for modeling submissions. The feedback is generated through the :ref:`Athena Service <athena_service>`, which analyzes both the **structure** and **layout** of the diagrams and produces feedback based on the provided **Grading Instructions**, **Problem Statement**, and **Sample Solution**.

.. admonition:: Note
   :class: note
    
    It is recommended that comprehensive **Grading Instructions** be provided in the form of **Structured Grading Instructions** and that a Sample Solution is included (although not mandatory). This ensures that the AI-generated feedback aligns with the intended grading criteria and offers targeted, meaningful hints.

**How to Request Automatic Feedback:**

1. **Requesting Feedback**

   .. container::

      - 1.1. Navigate to a **Modeling Exercise** with the **Automatic Student Feedback** feature enabled.
      - 1.2. Create a diagram in the modeling editor and submit it.
      - 1.3. Feedback may be requested either from the exercise overview page or directly within the modeling editor.

    .. figure:: modeling/automatic-feedback-request-editor.png
       :align: center
       :alt: Screenshot showing the request feedback button in the exercise overview and modeling editor
       :scale: 80%

2. **Viewing Feedback**

   .. container::

      - 2.1. After a feedback request is made, the system processes the diagram and generates preliminary feedback.
      - 2.2. An alert appears at the top of the page to indicate that the feedback is ready.

      .. figure:: modeling/automatic-feedback-request-alert.png
         :align: center
         :alt: Screenshot showing the notification alert when AI feedback is ready

      - 2.3. A preliminary score is displayed in the top-right corner of the screen.

      .. figure:: modeling/automatic-feedback-request-score.png
         :align: center
         :alt: Screenshot showing the preliminary score in the modeling editor

      - 2.4. Clicking on the score reveals detailed, inline feedback that highlights specific issues and provides suggestions directly within the diagram.

      .. figure:: modeling/automatic-feedback-view-detailed.png
         :align: center
         :alt: Screenshot showing detailed AI feedback
         :scale: 50%

3. **Submission History**

   .. container::

      - Feedback can be requested multiple times before the submission due date. All feedback requests are recorded in the submission history.
      - To review previous feedback, access the submission history section and click on an entry to display its detailed feedback.

      .. figure:: modeling/automatic-feedback-history.png
         :align: center
         :alt: Screenshot showing the submission history section in the modeling editor
         :scale: 50%

**Demo:**

A demonstration of the automated generation of student feedback for a class diagram can be found in the following screencast:

.. raw:: html

    <iframe src="https://tum.live/w/artemisintro/55922?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Video tutorial of the automated assessment of modeling exercises on TUM-Live.
    </iframe>

.. |edit| image:: modeling/edit.png
    :scale: 75
.. |course-management| image:: general/course-management.png
.. |save| image:: modeling/save.png
.. |start| image:: modeling/start.png
.. |open-modeling-editor| image:: modeling/open-modeling-editor.png
.. |hint| image:: modeling/hint.png
.. |create-example-submission| image:: modeling/create-example-submission.png
.. |use-as-example-submission| image:: modeling/use-as-example-submission.png
.. |add-new-feedback| image:: modeling/add-new-feedback.png
.. |assess-submission| image:: modeling/assess-submission.png
.. |scores| image:: modeling/scores.png
.. |participation| image:: modeling/participation.png
.. |submission| image:: modeling/submission.png
.. |example-submission| image:: modeling/example-submission.png
.. |delete| image:: modeling/delete.png
.. |submit| image:: modeling/submit.png
.. |cancel| image:: modeling/cancel.png
.. |exercise-dashboard-button| image:: modeling/exercise-dashboard-button.png
.. |import| image:: modeling/import.png
